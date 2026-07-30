// Package vault implements ottoconfig.Source backed by a HashiCorp Vault KV
// v2 secret engine, using the official github.com/hashicorp/vault/api client
// with AppRole or AWS-IAM authentication. It falls back to the local JSON
// file source for the "local"/"test"/"integration-test" profiles. It is a
// faithful port of Java's VaultSource/VaultClient/VaultAuthenticatorFactory.
package vault

import (
	"context"
	"fmt"
	"os"
	"sort"
	"strconv"
	"strings"

	vaultapi "github.com/hashicorp/vault/api"
	vaultapprole "github.com/hashicorp/vault/api/auth/approle"
	vaultaws "github.com/hashicorp/vault/api/auth/aws"

	ottoconfig "github.com/otto-de/otto-config/go"
	"github.com/otto-de/otto-config/go/domain"
	"github.com/otto-de/otto-config/go/source/file"
)

// Source loads domain.Properties from a single HashiCorp Vault KV v2 secret
// path, optionally merging previous secret versions as comma-joined values
// (to support zero-downtime secret rotation).
type Source struct {
	*ottoconfig.CachedSource

	client           *vaultapi.Client
	secretPath       string
	previousVersions int
}

var _ ottoconfig.Source = (*Source)(nil)

// New creates a Vault-backed Source for a single secret path (e.g.
// "secret/data/myapp"). previousVersions controls how many prior secret
// versions are merged in as additional comma-joined values.
func New(client *vaultapi.Client, secretPath string, previousVersions int) *Source {
	s := &Source{client: client, secretPath: secretPath, previousVersions: previousVersions}
	s.CachedSource = ottoconfig.NewCachedSource(s.load, func() ottoconfig.RawConfig { return domain.EmptyProperties() })
	return s
}

// Kind returns domain.PropertiesKind.
func (s *Source) Kind() string { return domain.PropertiesKind }

// HasSecrets always returns true for Vault-sourced properties.
func (s *Source) HasSecrets() bool { return true }

func (s *Source) load() (ottoconfig.RawConfig, error) {
	secret, err := s.client.Logical().Read(s.secretPath)
	if err != nil {
		return domain.EmptyProperties(), fmt.Errorf("unable to get secrets from vault: %w", err)
	}

	secrets := secretDataStrings(secret)
	if len(secrets) == 0 {
		return domain.EmptyProperties(), nil
	}

	merged, err := s.appendVersions(secrets)
	if err != nil {
		return domain.EmptyProperties(), fmt.Errorf("unable to get secrets from vault: %w", err)
	}
	return domain.NewProperties(merged), nil
}

// appendVersions reads up to previousVersions prior (non-deleted) secret
// versions and merges their values into secrets as comma-joined lists,
// mirroring Java's VaultSource.appendVersions/mergeAsListValues.
func (s *Source) appendVersions(secrets map[string]string) (map[string]string, error) {
	merged := make(map[string]string, len(secrets))
	for k, v := range secrets {
		merged[k] = v
	}

	versions, err := s.getPreviousVersions()
	if err != nil {
		return nil, err
	}

	for _, version := range versions {
		secret, err := s.client.Logical().ReadWithData(s.secretPath, map[string][]string{
			"version": {strconv.Itoa(version)},
		})
		if err != nil {
			return nil, err
		}
		previous := secretDataStrings(secret)
		for k, v := range previous {
			if existing, ok := merged[k]; ok {
				merged[k] = existing + "," + v
			} else {
				merged[k] = v
			}
		}
	}

	return merged, nil
}

// getPreviousVersions reads the secret's metadata, filters out deleted
// versions, sorts descending, skips the most-recent (current) version, and
// returns up to previousVersions of the rest.
func (s *Source) getPreviousVersions() ([]int, error) {
	metadataPath := strings.Replace(s.secretPath, "/data/", "/metadata/", 1)
	secret, err := s.client.Logical().Read(metadataPath)
	if err != nil {
		return nil, err
	}
	if secret == nil || secret.Data == nil {
		return nil, nil
	}
	rawVersions, ok := secret.Data["versions"].(map[string]interface{})
	if !ok {
		return nil, nil
	}

	var versions []int
	for k, v := range rawVersions {
		n, err := strconv.Atoi(k)
		if err != nil {
			continue
		}
		info, ok := v.(map[string]interface{})
		if !ok {
			continue
		}
		deletionTime, _ := info["deletion_time"].(string)
		if deletionTime != "" {
			continue
		}
		versions = append(versions, n)
	}

	sort.Sort(sort.Reverse(sort.IntSlice(versions)))
	if len(versions) <= 1 {
		return nil, nil
	}
	versions = versions[1:]
	if len(versions) > s.previousVersions {
		versions = versions[:s.previousVersions]
	}
	return versions, nil
}

// secretDataStrings extracts the KV v2 "data" map from a Vault secret
// response as a map[string]string.
func secretDataStrings(secret *vaultapi.Secret) map[string]string {
	if secret == nil || secret.Data == nil {
		return nil
	}
	inner, ok := secret.Data["data"].(map[string]interface{})
	if !ok {
		return nil
	}
	out := make(map[string]string, len(inner))
	for k, v := range inner {
		out[k] = fmt.Sprint(v)
	}
	return out
}

func init() {
	ottoconfig.RegisterSourceFactory("hashicorp.vault", func(ctx *ottoconfig.Context) (ottoconfig.Source, error) {
		if file.ShouldFallback(ctx, os.DirFS("."), file.DefaultFileName) {
			return file.NewProperties(os.DirFS("."), file.DefaultFileName, ""), nil
		}

		secretPath, ok := ottoconfig.GetValueAsString(ctx.Configuration(), "otto.config.hashicorp.vault.path")
		if !ok || secretPath == "" {
			return nil, fmt.Errorf("missing required configuration key: otto.config.hashicorp.vault.path")
		}

		client, err := ottoconfig.GetOrRegisterClientErr(ctx.ClientRegistry(), func() (*vaultapi.Client, error) {
			return newAuthenticatedClient(ctx)
		})
		if err != nil {
			return nil, err
		}

		previousVersions := ottoconfig.GetValueAsInt(ctx.Configuration(), "otto.config.hashicorp.vault.prev.versions", 1)

		var sources []ottoconfig.Source
		for _, path := range strings.Split(secretPath, ",") {
			sources = append(sources, New(client, path, previousVersions))
		}
		return ottoconfig.NewCombinedPropertiesSource(sources), nil
	})
}

func newAuthenticatedClient(ctx *ottoconfig.Context) (*vaultapi.Client, error) {
	vaultURL, ok := ottoconfig.GetValueAsString(ctx.Configuration(), "otto.config.hashicorp.vault.url")
	if !ok || vaultURL == "" {
		return nil, fmt.Errorf("missing required configuration key: otto.config.hashicorp.vault.url")
	}

	cfg := vaultapi.DefaultConfig()
	cfg.Address = vaultURL
	client, err := vaultapi.NewClient(cfg)
	if err != nil {
		return nil, err
	}

	authType := ottoconfig.GetValueAsStringOr(ctx.Configuration(), "otto.config.hashicorp.vault.auth.type", "approle")

	var authMethod vaultapi.AuthMethod
	switch authType {
	case "aws":
		var opts []vaultaws.LoginOption
		if roleName, ok := ottoconfig.GetValueAsString(ctx.Configuration(), "otto.config.hashicorp.vault.auth.aws.role.name"); ok && roleName != "" {
			opts = append(opts, vaultaws.WithRole(roleName))
		}
		if headerValue, ok := ottoconfig.GetValueAsString(ctx.Configuration(), "otto.config.hashicorp.vault.auth.aws.header.value"); ok && headerValue != "" {
			opts = append(opts, vaultaws.WithIAMServerIDHeader(headerValue))
		}
		if region, ok := ottoconfig.GetValueAsString(ctx.Configuration(), "otto.config.hashicorp.vault.auth.aws.region"); ok && region != "" {
			opts = append(opts, vaultaws.WithRegion(region))
		}
		opts = append(opts, vaultaws.WithIAMAuth())
		am, err := vaultaws.NewAWSAuth(opts...)
		if err != nil {
			return nil, err
		}
		authMethod = am
	default:
		roleID, ok := ottoconfig.GetValueAsString(ctx.Configuration(), "otto.config.hashicorp.vault.auth.approle.role.id")
		if !ok || roleID == "" {
			return nil, fmt.Errorf("missing required configuration key: otto.config.hashicorp.vault.auth.approle.role.id")
		}
		secretID, ok := ottoconfig.GetValueAsString(ctx.Configuration(), "otto.config.hashicorp.vault.auth.approle.secret.id")
		if !ok || secretID == "" {
			return nil, fmt.Errorf("missing required configuration key: otto.config.hashicorp.vault.auth.approle.secret.id")
		}
		am, err := vaultapprole.NewAppRoleAuth(roleID, &vaultapprole.SecretID{FromString: secretID})
		if err != nil {
			return nil, err
		}
		authMethod = am
	}

	authInfo, err := client.Auth().Login(context.Background(), authMethod)
	if err != nil {
		return nil, fmt.Errorf("vault login failed: %w", err)
	}
	if authInfo == nil {
		return nil, fmt.Errorf("vault login returned no auth info")
	}
	client.SetToken(authInfo.Auth.ClientToken)

	return client, nil
}
