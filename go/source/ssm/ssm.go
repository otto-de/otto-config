// Package ssm implements ottoconfig.Source backed by AWS Systems Manager
// Parameter Store, reading all parameters recursively under one or more path
// prefixes (with decryption for SecureString values), plus a legacy
// "/.../config/{service}/{key}" naming-convention property alias. It falls
// back to the local JSON file source for the "local"/"test"/
// "integration-test" profiles. It is a faithful port of Java's SsmSource.
package ssm

import (
	"context"
	"fmt"
	"os"
	"regexp"
	"strings"

	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/ssm"
	"github.com/aws/aws-sdk-go-v2/service/ssm/types"

	ottoconfig "github.com/otto-de/otto-config/go"
	"github.com/otto-de/otto-config/go/domain"
	"github.com/otto-de/otto-config/go/event"
	"github.com/otto-de/otto-config/go/source/file"
)

var legacyFormat = regexp.MustCompile(`^/[^/]+/[^/]+/([^/]+)/[^/]+/(.+)$`)

// Source loads domain.Properties from AWS SSM Parameter Store parameters
// found recursively under a single path prefix.
type Source struct {
	*ottoconfig.CachedSource

	applicationIdentifier string
	client                *ssm.Client
	ssmPathPrefix         string
	pullRefreshEnabled    bool
	excludeSecrets        bool
}

var _ ottoconfig.Source = (*Source)(nil)

// New creates an SSM-backed Source for a single path prefix.
func New(applicationIdentifier string, client *ssm.Client, ssmPathPrefix string, pullRefreshEnabled, excludeSecrets bool) *Source {
	s := &Source{
		applicationIdentifier: applicationIdentifier,
		client:                client,
		ssmPathPrefix:         ssmPathPrefix,
		pullRefreshEnabled:    pullRefreshEnabled,
		excludeSecrets:        excludeSecrets,
	}
	s.CachedSource = ottoconfig.NewCachedSource(s.load, func() ottoconfig.RawConfig { return domain.EmptyProperties() })
	return s
}

// Kind returns domain.PropertiesKind.
func (s *Source) Kind() string { return domain.PropertiesKind }

// HasSecrets always returns true, since SSM sources may contain
// SecureString parameters.
func (s *Source) HasSecrets() bool { return true }

// PullRefreshEnabled reports whether polling is enabled for this source.
func (s *Source) PullRefreshEnabled() bool { return s.pullRefreshEnabled }

// OnChanged reports whether a parsed SsmParameterChangeEvent's parameter
// name falls under this source's path prefix, mirroring Java's
// SsmSource.onChanged.
func (s *Source) OnChanged(e ottoconfig.ChangeEvent) bool {
	evt, ok := e.(event.SsmParameterChangeEvent)
	if !ok {
		return false
	}
	return strings.HasPrefix(evt.ParameterName, s.ssmPathPrefix)
}

func (s *Source) load() (ottoconfig.RawConfig, error) {
	properties := make(map[string]string)

	paginator := ssm.NewGetParametersByPathPaginator(s.client, &ssm.GetParametersByPathInput{
		Path:           &s.ssmPathPrefix,
		WithDecryption: boolPtr(true),
		Recursive:      boolPtr(true),
	})

	for paginator.HasMorePages() {
		page, err := paginator.NextPage(context.Background())
		if err != nil {
			return domain.EmptyProperties(), fmt.Errorf("could not load otto config properties from SSM: %w", err)
		}
		for _, param := range page.Parameters {
			name := stringOf(param.Name)
			value := stringOf(param.Value)

			if s.excludeSecrets && param.Type == types.ParameterTypeSecureString {
				continue
			}
			properties[name] = value
			s.addServiceLevelProperty(properties, name, value)
		}
	}

	return domain.NewProperties(properties), nil
}

func (s *Source) addServiceLevelProperty(properties map[string]string, name, value string) {
	if !strings.Contains(name, "/config/") {
		return
	}
	m := legacyFormat.FindStringSubmatch(name)
	if m == nil {
		return
	}
	service, key := m[1], m[2]
	if service == s.applicationIdentifier {
		properties[key] = value
	} else {
		properties[fmt.Sprintf("%s/%s", service, key)] = value
	}
}

func boolPtr(b bool) *bool { return &b }

func stringOf(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

func init() {
	ottoconfig.RegisterSourceFactory("aws.ssm", func(ctx *ottoconfig.Context) (ottoconfig.Source, error) {
		if file.ShouldFallback(ctx, os.DirFS("."), file.DefaultFileName) {
			return file.NewProperties(os.DirFS("."), file.DefaultFileName, ""), nil
		}

		ssmPath, ok := ottoconfig.GetValueAsString(ctx.Configuration(), ctx.AppName()+".otto.config.aws.ssm.path.prefix")
		if !ok || ssmPath == "" {
			ssmPath = ottoconfig.GetValueAsStringOr(ctx.Configuration(), "otto.config.aws.ssm.path.prefix", "/")
		}

		cfg, err := awsconfig.LoadDefaultConfig(context.Background())
		if err != nil {
			return nil, err
		}
		client := ottoconfig.GetOrRegisterClient(ctx.ClientRegistry(), func() *ssm.Client {
			return ssm.NewFromConfig(cfg)
		})

		pullRefreshEnabled := !ottoconfig.GetValueAsBool(ctx.Configuration(), "otto.config.aws.change.notifications.enabled", false)

		var sources []ottoconfig.Source
		for _, path := range strings.Split(ssmPath, ",") {
			sources = append(sources, New(ctx.AppName(), client, path, pullRefreshEnabled, ctx.ExcludeSecrets()))
		}
		return ottoconfig.NewCombinedPropertiesSource(sources), nil
	})
}
