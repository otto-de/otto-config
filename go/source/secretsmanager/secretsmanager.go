// Package secretsmanager implements ottoconfig.Source backed by AWS Secrets
// Manager, reading both the AWSCURRENT and AWSPREVIOUS version stages of one
// or more secret ARNs and tagging each key with its version stage so that
// callers can observe in-flight secret rotations. It falls back to the local
// JSON file source for the "local"/"test"/"integration-test" profiles. It is
// a faithful port of Java's SecretsManagerSource/CombinedPropertySource.
package secretsmanager

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"regexp"
	"sort"
	"strings"

	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/secretsmanager"
	"github.com/aws/aws-sdk-go-v2/service/secretsmanager/types"

	ottoconfig "github.com/otto-de/otto-config/go"
	"github.com/otto-de/otto-config/go/domain"
	"github.com/otto-de/otto-config/go/event"
	"github.com/otto-de/otto-config/go/source/file"
)

var versionStages = []string{"AWSCURRENT", "AWSPREVIOUS"}

var versionSuffixPattern = regexp.MustCompile(`_(AWSCURRENT|AWSPREVIOUS)$`)

// Source loads domain.Properties from a single AWS Secrets Manager secret,
// merging the AWSCURRENT and AWSPREVIOUS version stages.
type Source struct {
	*ottoconfig.CachedSource

	client             *secretsmanager.Client
	secretARN          string
	pullRefreshEnabled bool
}

var _ ottoconfig.Source = (*Source)(nil)

// New creates a Secrets-Manager-backed Source for a single secret ARN.
func New(client *secretsmanager.Client, secretARN string, pullRefreshEnabled bool) *Source {
	s := &Source{client: client, secretARN: secretARN, pullRefreshEnabled: pullRefreshEnabled}
	s.CachedSource = ottoconfig.NewCachedSource(s.load, func() ottoconfig.RawConfig { return domain.EmptyProperties() })
	return s
}

// Kind returns domain.PropertiesKind.
func (s *Source) Kind() string { return domain.PropertiesKind }

// HasSecrets always returns true for Secrets Manager sourced properties.
func (s *Source) HasSecrets() bool { return true }

// PullRefreshEnabled reports whether polling is enabled for this source.
func (s *Source) PullRefreshEnabled() bool { return s.pullRefreshEnabled }

// OnChanged reports whether a parsed SecretsManagerChangeEvent pertains to
// this source's secret ARN, mirroring Java's SecretsManagerSource.onChanged.
// The event's secretId may be the full ARN or just the secret name, so
// match if either value contains the other.
func (s *Source) OnChanged(e ottoconfig.ChangeEvent) bool {
	evt, ok := e.(event.SecretsManagerChangeEvent)
	if !ok {
		return false
	}
	return strings.Contains(evt.SecretID, s.secretARN) || strings.Contains(s.secretARN, evt.SecretID)
}

type entry struct {
	key   string
	value string
}

func (s *Source) load() (ottoconfig.RawConfig, error) {
	var entries []entry
	for _, stage := range versionStages {
		values, actualStage, err := s.getSecretValue(stage)
		if err != nil {
			return domain.EmptyProperties(), fmt.Errorf("could not load otto config properties from secrets manager: %w", err)
		}
		for k, v := range values {
			entries = append(entries, entry{key: fmt.Sprintf("%s_%s", k, actualStage), value: v})
		}
	}

	combined := mergeEntriesAsListValues(entries, func(key string) string {
		return versionSuffixPattern.ReplaceAllString(key, "")
	})

	return domain.NewProperties(combined), nil
}

// getSecretValue fetches the secret for a version stage, returning an empty
// map (not an error) if the stage doesn't exist for this secret.
func (s *Source) getSecretValue(versionStage string) (map[string]string, string, error) {
	resp, err := s.client.GetSecretValue(context.Background(), &secretsmanager.GetSecretValueInput{
		SecretId:     &s.secretARN,
		VersionStage: &versionStage,
	})
	if err != nil {
		var notFound *types.ResourceNotFoundException
		if errors.As(err, &notFound) {
			return map[string]string{}, versionStage, nil
		}
		return nil, versionStage, err
	}

	actualStage := versionStage
	if len(resp.VersionStages) > 0 {
		actualStage = resp.VersionStages[0]
	}

	var values map[string]string
	secretString := ""
	if resp.SecretString != nil {
		secretString = *resp.SecretString
	} else {
		secretString = "{}"
	}
	if err := json.Unmarshal([]byte(secretString), &values); err != nil {
		return nil, actualStage, err
	}
	return values, actualStage, nil
}

// mergeEntriesAsListValues groups entries by keyFormatter(key), sorting by
// (key, value) and joining grouped values with commas -- mirroring Java's
// PropertySource.mergeEntriesAsListValues.
func mergeEntriesAsListValues(entries []entry, keyFormatter func(string) string) map[string]string {
	sort.Slice(entries, func(i, j int) bool {
		if entries[i].key != entries[j].key {
			return entries[i].key < entries[j].key
		}
		return entries[i].value < entries[j].value
	})

	order := make([]string, 0, len(entries))
	grouped := make(map[string][]string)
	for _, e := range entries {
		k := keyFormatter(e.key)
		if _, ok := grouped[k]; !ok {
			order = append(order, k)
		}
		grouped[k] = append(grouped[k], e.value)
	}

	result := make(map[string]string, len(grouped))
	for _, k := range order {
		result[k] = strings.Join(grouped[k], ",")
	}
	return result
}

func init() {
	ottoconfig.RegisterSourceFactory("aws.secrets", func(ctx *ottoconfig.Context) (ottoconfig.Source, error) {
		if file.ShouldFallback(ctx, os.DirFS("."), file.DefaultFileName) {
			return file.NewProperties(os.DirFS("."), file.DefaultFileName, ""), nil
		}

		secretARN, ok := ottoconfig.GetValueAsString(ctx.Configuration(), "otto.config.aws.secrets.arn")
		if !ok || secretARN == "" {
			return nil, fmt.Errorf("missing required configuration key: otto.config.aws.secrets.arn")
		}

		cfg, err := awsconfig.LoadDefaultConfig(context.Background())
		if err != nil {
			return nil, err
		}
		client := ottoconfig.GetOrRegisterClient(ctx.ClientRegistry(), func() *secretsmanager.Client {
			return secretsmanager.NewFromConfig(cfg)
		})

		pullRefreshEnabled := !ottoconfig.GetValueAsBool(ctx.Configuration(), "otto.config.aws.change.notifications.enabled", false)

		var sources []ottoconfig.Source
		for _, arn := range strings.Split(secretARN, ",") {
			sources = append(sources, New(client, arn, pullRefreshEnabled))
		}
		return ottoconfig.NewCombinedPropertiesSource(sources), nil
	})
}
