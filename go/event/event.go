// Package event implements event-driven configuration refresh: parsing
// EventBridge notifications delivered via SQS (as forwarded by AWS AppConfig,
// Secrets Manager, and SSM change events) and dispatching them to matching
// Sources via Source.OnChanged/Refresh. It registers itself as a
// ottoconfig.ChangeListener factory, activated when
// "otto.config.aws.change.notifications.enabled" is true and
// "otto.config.aws.change.notifications.queue.url" is set. It is a faithful
// port of Java's AwsChangeEventListener/ChangeEventParser and the
// core.aws.event.* record types.
package event

import (
	"context"
	"encoding/json"
	"log/slog"

	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/sqs"
	"github.com/aws/aws-sdk-go-v2/service/sqs/types"

	ottoconfig "github.com/otto-de/otto-config/go"
)

// AppConfigDeploymentEvent notifies that an AWS AppConfig configuration
// profile has a new deployment. It mirrors Java's AppConfigDeploymentEvent.
type AppConfigDeploymentEvent struct {
	Src               string
	Typ               string
	ApplicationID     string
	ApplicationName   string
	EnvironmentID     string
	EnvironmentName   string
	ConfigProfileID   string
	ConfigProfileName string
}

// EventSource returns the EventBridge "source" field (e.g. "aws.appconfig").
func (e AppConfigDeploymentEvent) EventSource() string { return e.Src }

// DetailType returns the EventBridge "detail-type" field.
func (e AppConfigDeploymentEvent) DetailType() string { return e.Typ }

var _ ottoconfig.ChangeEvent = AppConfigDeploymentEvent{}

// SecretsManagerChangeEvent notifies that an AWS Secrets Manager secret
// changed. It mirrors Java's SecretsManagerChangeEvent.
type SecretsManagerChangeEvent struct {
	Src      string
	Typ      string
	SecretID string
}

func (e SecretsManagerChangeEvent) EventSource() string { return e.Src }
func (e SecretsManagerChangeEvent) DetailType() string  { return e.Typ }

var _ ottoconfig.ChangeEvent = SecretsManagerChangeEvent{}

// SsmParameterChangeEvent notifies that an AWS SSM parameter changed. It
// mirrors Java's SsmParameterChangeEvent.
type SsmParameterChangeEvent struct {
	Src           string
	Typ           string
	ParameterName string
	Operation     string
}

func (e SsmParameterChangeEvent) EventSource() string { return e.Src }
func (e SsmParameterChangeEvent) DetailType() string  { return e.Typ }

var _ ottoconfig.ChangeEvent = SsmParameterChangeEvent{}

// UnknownChangeEvent represents an EventBridge event from an unrecognised
// source. It mirrors Java's UnknownChangeEvent.
type UnknownChangeEvent struct {
	Src string
	Typ string
}

func (e UnknownChangeEvent) EventSource() string { return e.Src }
func (e UnknownChangeEvent) DetailType() string  { return e.Typ }

var _ ottoconfig.ChangeEvent = UnknownChangeEvent{}

type envelope struct {
	Source     string          `json:"source"`
	DetailType string          `json:"detail-type"`
	Detail     json.RawMessage `json:"detail"`
}

// Parse decodes an EventBridge notification (as delivered via SQS) into the
// appropriate ChangeEvent, falling back to UnknownChangeEvent for
// unrecognised sources. It mirrors Java's ChangeEventParser.parse.
func Parse(messageBody []byte) (ottoconfig.ChangeEvent, error) {
	var env envelope
	if err := json.Unmarshal(messageBody, &env); err != nil {
		return nil, err
	}

	var detail map[string]any
	if len(env.Detail) > 0 {
		if err := json.Unmarshal(env.Detail, &detail); err != nil {
			return nil, err
		}
	}

	switch env.Source {
	case "aws.appconfig":
		return parseAppConfigEvent(env.Source, env.DetailType, detail), nil
	case "aws.secretsmanager":
		return parseSecretsManagerEvent(env.Source, env.DetailType, detail), nil
	case "aws.ssm":
		return parseSsmEvent(env.Source, env.DetailType, detail), nil
	default:
		return UnknownChangeEvent{Src: env.Source, Typ: env.DetailType}, nil
	}
}

func parseAppConfigEvent(source, detailType string, detail map[string]any) AppConfigDeploymentEvent {
	// Regions that support native AppConfig EventBridge events use
	// detail-type "AWS AppConfig Deployment Status" with top-level fields.
	// Other regions (e.g. eu-central-1) deliver CloudTrail management events
	// with detail-type "AWS API Call via CloudTrail"; the IDs are nested
	// under requestParameters / responseElements.
	if detailType == "AWS API Call via CloudTrail" {
		params, _ := detail["requestParameters"].(map[string]any)
		response, _ := detail["responseElements"].(map[string]any)
		return AppConfigDeploymentEvent{
			Src:               source,
			Typ:               detailType,
			ApplicationID:     stringAt(params, "applicationId"),
			ApplicationName:   "", // application name is not present in CloudTrail events
			EnvironmentID:     stringAt(params, "environmentId"),
			EnvironmentName:   "",
			ConfigProfileID:   stringAt(params, "configurationProfileId"),
			ConfigProfileName: stringAt(response, "configurationName"), // profile name, e.g. "properties"
		}
	}
	// Native "AWS AppConfig Deployment Status" event format.
	return AppConfigDeploymentEvent{
		Src:               source,
		Typ:               detailType,
		ApplicationID:     stringAt(detail, "application-id"),
		ApplicationName:   stringAt(detail, "application-name"),
		EnvironmentID:     stringAt(detail, "environment-id"),
		EnvironmentName:   stringAt(detail, "environment-name"),
		ConfigProfileID:   stringAt(detail, "configuration-profile-id"),
		ConfigProfileName: stringAt(detail, "configuration-profile-name"),
	}
}

func parseSecretsManagerEvent(source, detailType string, detail map[string]any) SecretsManagerChangeEvent {
	// Secrets Manager events arrive via CloudTrail; the secret identifier is
	// nested under detail.requestParameters.secretId.
	params, _ := detail["requestParameters"].(map[string]any)
	return SecretsManagerChangeEvent{
		Src:      source,
		Typ:      detailType,
		SecretID: stringAt(params, "secretId"),
	}
}

func parseSsmEvent(source, detailType string, detail map[string]any) SsmParameterChangeEvent {
	return SsmParameterChangeEvent{
		Src:           source,
		Typ:           detailType,
		ParameterName: stringAt(detail, "name"),
		Operation:     stringAt(detail, "operation"),
	}
}

func stringAt(m map[string]any, key string) string {
	if m == nil {
		return ""
	}
	if s, ok := m[key].(string); ok {
		return s
	}
	return ""
}

// Listener polls an SQS queue for EventBridge change notifications and
// dispatches them to the sources registered on its Context. It mirrors
// Java's AwsChangeEventListener.
type Listener struct {
	client   *sqs.Client
	queueURL string
	ctx      *ottoconfig.Context
}

var _ ottoconfig.ChangeListener = (*Listener)(nil)

// NewListener creates a Listener polling queueURL for events relevant to
// ctx's registered sources.
func NewListener(client *sqs.Client, queueURL string, ctx *ottoconfig.Context) *Listener {
	return &Listener{client: client, queueURL: queueURL, ctx: ctx}
}

// PollAndRefresh receives up to 10 messages (20s long-poll), parses and
// dispatches each to matching sources, and always deletes every received
// message (poison-pill safe: unrecognised/failed messages are not
// re-delivered; genuine retries are handled by the safety-net polling
// refresh).
func (l *Listener) PollAndRefresh() {
	messages, err := l.receiveMessages()
	if err != nil {
		slog.Default().Error("error receiving SQS messages", "queueUrl", l.queueURL, "error", err)
		return
	}
	if len(messages) == 0 {
		return
	}

	toDelete := make([]types.DeleteMessageBatchRequestEntry, 0, len(messages))
	for _, message := range messages {
		if message.Body != nil {
			event, err := Parse([]byte(*message.Body))
			if err != nil {
				slog.Default().Error("failed to process SQS message, discarding to avoid poison-pill loop",
					"messageId", stringPtrOf(message.MessageId), "error", err)
			} else {
				l.dispatch(event)
			}
		}
		toDelete = append(toDelete, types.DeleteMessageBatchRequestEntry{
			Id:            message.MessageId,
			ReceiptHandle: message.ReceiptHandle,
		})
	}

	l.deleteMessages(toDelete)
}

func (l *Listener) dispatch(event ottoconfig.ChangeEvent) {
	for _, source := range l.ctx.SourceRegistry().Values() {
		if source.OnChanged(event) {
			source.Refresh()
		}
	}
}

func (l *Listener) receiveMessages() ([]types.Message, error) {
	resp, err := l.client.ReceiveMessage(context.Background(), &sqs.ReceiveMessageInput{
		QueueUrl:            &l.queueURL,
		WaitTimeSeconds:     20,
		MaxNumberOfMessages: 10,
	})
	if err != nil {
		return nil, err
	}
	return resp.Messages, nil
}

func (l *Listener) deleteMessages(entries []types.DeleteMessageBatchRequestEntry) {
	if len(entries) == 0 {
		return
	}
	_, err := l.client.DeleteMessageBatch(context.Background(), &sqs.DeleteMessageBatchInput{
		QueueUrl: &l.queueURL,
		Entries:  entries,
	})
	if err != nil {
		slog.Default().Error("error deleting SQS messages", "queueUrl", l.queueURL, "error", err)
	}
}

func stringPtrOf(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

func init() {
	ottoconfig.RegisterChangeListenerFactory(func(ctx *ottoconfig.Context) (ottoconfig.ChangeListener, bool, error) {
		enabled := ottoconfig.GetValueAsBool(ctx.Configuration(), "otto.config.aws.change.notifications.enabled", false)
		queueURL := ottoconfig.GetValueAsStringOr(ctx.Configuration(), "otto.config.aws.change.notifications.queue.url", "")
		if !enabled || queueURL == "" {
			return nil, false, nil
		}

		cfg, err := awsconfig.LoadDefaultConfig(context.Background())
		if err != nil {
			return nil, false, err
		}
		client := ottoconfig.GetOrRegisterClient(ctx.ClientRegistry(), func() *sqs.Client {
			return sqs.NewFromConfig(cfg)
		})

		return NewListener(client, queueURL, ctx), true, nil
	})
}
