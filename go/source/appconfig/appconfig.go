// Package appconfig implements ottoconfig.Source backed by AWS AppConfig
// (via the appconfigdata "GetLatestConfiguration" long-poll API), with a
// local JSON file fallback for the "local"/"test"/"integration-test"
// profiles. It is a faithful port of Java's AppConfigSource/CoreSourceFactory.
package appconfig

import (
	"context"
	"encoding/json"
	"fmt"
	"os"

	"github.com/aws/aws-sdk-go-v2/aws"
	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/appconfigdata"

	ottoconfig "github.com/otto-de/otto-config/go"
	"github.com/otto-de/otto-config/go/domain"
	"github.com/otto-de/otto-config/go/event"
	"github.com/otto-de/otto-config/go/source/file"
)

// environmentIdentifier is hardcoded to "local" to match the Java
// implementation (AppConfigSource.environmentIdentifier is fixed at "local").
const environmentIdentifier = "local"

// Source loads a RawConfig from an AWS AppConfig configuration profile using
// the long-poll GetLatestConfiguration token protocol.
type Source struct {
	*ottoconfig.CachedSource

	applicationIdentifier          string
	configurationProfileIdentifier string
	client                         *appconfigdata.Client
	kind                           string
	unmarshal                      func([]byte) (ottoconfig.RawConfig, error)

	pullRefreshEnabled bool
	configurationToken string
}

var _ ottoconfig.Source = (*Source)(nil)

// New creates an AppConfig-backed Source.
func New(
	applicationIdentifier string,
	configurationProfileIdentifier string,
	client *appconfigdata.Client,
	kind string,
	unmarshal func([]byte) (ottoconfig.RawConfig, error),
	empty func() ottoconfig.RawConfig,
	pullRefreshEnabled bool,
) *Source {
	s := &Source{
		applicationIdentifier:          applicationIdentifier,
		configurationProfileIdentifier: configurationProfileIdentifier,
		client:                         client,
		kind:                           kind,
		unmarshal:                      unmarshal,
		pullRefreshEnabled:             pullRefreshEnabled,
	}
	s.CachedSource = ottoconfig.NewCachedSource(s.load, empty)
	return s
}

// Kind reports whether this source produces "properties" or "toggles".
func (s *Source) Kind() string {
	return s.kind
}

// PullRefreshEnabled reports whether polling is enabled for this source, per
// "otto.config.aws.change.notifications.enabled".
func (s *Source) PullRefreshEnabled() bool {
	return s.pullRefreshEnabled
}

// OnChanged reports whether a parsed AppConfigDeploymentEvent pertains to
// this source's application/configuration-profile, mirroring Java's
// AppConfigSource.onChanged.
func (s *Source) OnChanged(e ottoconfig.ChangeEvent) bool {
	evt, ok := e.(event.AppConfigDeploymentEvent)
	if !ok {
		return false
	}
	// AppConfig EventBridge events always include application-id and
	// configuration-profile-id (AWS-generated IDs), but application-name and
	// configuration-profile-name are absent in some schema versions and
	// default to "". applicationIdentifier and configurationProfileIdentifier
	// hold the human-readable names used to start the session, so direct ID
	// comparison will never match.
	//
	// Application match: compare by name if the event carries it; otherwise
	// trust the EventBridge rule, which already filters on application-id, so
	// any arriving event belongs to our application.
	appMatches := s.applicationIdentifier == evt.ApplicationID ||
		s.applicationIdentifier == evt.ApplicationName ||
		evt.ApplicationName == ""

	// Profile match: compare by name if the event carries it; otherwise
	// accept all profiles (GetLatestConfiguration returns empty content for
	// unchanged profiles, making the extra reload a cheap no-op).
	profileMatches := s.configurationProfileIdentifier == evt.ConfigProfileID ||
		s.configurationProfileIdentifier == evt.ConfigProfileName ||
		evt.ConfigProfileName == ""

	return appMatches && profileMatches
}

func (s *Source) load() (ottoconfig.RawConfig, error) {
	empty := s.CachedSource.Empty()

	s.ensureConfigurationToken()

	if s.configurationToken == "" {
		return empty, nil
	}

	resp, err := s.client.GetLatestConfiguration(context.Background(), &appconfigdata.GetLatestConfigurationInput{
		ConfigurationToken: aws.String(s.configurationToken),
	})
	if err != nil {
		return empty, fmt.Errorf(
			"could not load configuration profile for applicationIdentifier: %s, environmentIdentifier: %s, configurationProfileIdentifier: %s: %w",
			s.applicationIdentifier, environmentIdentifier, s.configurationProfileIdentifier, err)
	}

	s.configurationToken = aws.ToString(resp.NextPollConfigurationToken)

	if len(resp.Configuration) == 0 {
		return empty, nil
	}

	rc, err := s.unmarshal(resp.Configuration)
	if err != nil {
		return empty, fmt.Errorf(
			"could not unmarshal configuration profile for applicationIdentifier: %s, environmentIdentifier: %s, configurationProfileIdentifier: %s: %w",
			s.applicationIdentifier, environmentIdentifier, s.configurationProfileIdentifier, err)
	}
	return rc, nil
}

func (s *Source) ensureConfigurationToken() {
	if s.configurationToken == "" {
		s.configurationToken = s.initializeConfigurationToken()
	}
}

func (s *Source) initializeConfigurationToken() string {
	resp, err := s.client.StartConfigurationSession(context.Background(), &appconfigdata.StartConfigurationSessionInput{
		ApplicationIdentifier:          aws.String(s.applicationIdentifier),
		EnvironmentIdentifier:          aws.String(environmentIdentifier),
		ConfigurationProfileIdentifier: aws.String(s.configurationProfileIdentifier),
	})
	if err != nil {
		return ""
	}
	return aws.ToString(resp.InitialConfigurationToken)
}

func init() {
	ottoconfig.RegisterSourceFactory("aws.appconfig.properties", func(ctx *ottoconfig.Context) (ottoconfig.Source, error) {
		return createAppConfigSource(ctx, "properties", domain.PropertiesKind, "",
			unmarshalProperties, func() ottoconfig.RawConfig { return domain.EmptyProperties() })
	})

	ottoconfig.RegisterSourceFactory("aws.appconfig.toggles", func(ctx *ottoconfig.Context) (ottoconfig.Source, error) {
		return createAppConfigSource(ctx, "toggles", domain.TogglesKind, "toggles",
			unmarshalToggles, func() ottoconfig.RawConfig { return domain.EmptyToggles() })
	})
}

// unmarshalProperties parses the raw AppConfigData "properties" profile
// content, which is a JSON document with a top-level "properties" object
// (mirroring Java's Properties Jackson shape, see docs/AWS_SETUP.md), e.g.
// {"properties": {"myKey": "myValue"}}. A missing "properties" key yields
// an empty Properties rather than an error.
func unmarshalProperties(data []byte) (ottoconfig.RawConfig, error) {
	var root map[string]json.RawMessage
	if err := json.Unmarshal(data, &root); err != nil {
		return nil, err
	}
	p := domain.EmptyProperties()
	if sectionData, ok := root["properties"]; ok {
		if err := json.Unmarshal(sectionData, p); err != nil {
			return nil, err
		}
	}
	return p, nil
}

func unmarshalToggles(data []byte) (ottoconfig.RawConfig, error) {
	t := domain.EmptyToggles()
	if err := json.Unmarshal(data, t); err != nil {
		return nil, err
	}
	return t, nil
}

func createAppConfigSource(
	ctx *ottoconfig.Context,
	configurationProfileIdentifier string,
	kind string,
	section string,
	unmarshal func([]byte) (ottoconfig.RawConfig, error),
	empty func() ottoconfig.RawConfig,
) (ottoconfig.Source, error) {
	if file.ShouldFallback(ctx, os.DirFS("."), file.DefaultFileName) {
		return file.New(os.DirFS("."), file.DefaultFileName, section, kind, unmarshal, empty), nil
	}

	cfg, err := awsconfig.LoadDefaultConfig(context.Background())
	if err != nil {
		return nil, err
	}
	client := ottoconfig.GetOrRegisterClient(ctx.ClientRegistry(), func() *appconfigdata.Client {
		return appconfigdata.NewFromConfig(cfg)
	})

	pullRefreshEnabled := !ottoconfig.GetValueAsBool(ctx.Configuration(), "otto.config.aws.change.notifications.enabled", false)

	return New(
		ctx.AppName(),
		configurationProfileIdentifier,
		client,
		kind,
		unmarshal,
		empty,
		pullRefreshEnabled,
	), nil
}
