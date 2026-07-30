// Package s3toggles implements ottoconfig.Source backed by AWS S3, reading
// feature toggle state purely from object key naming convention (on.<name>
// / off.<name> under a configured prefix) without ever reading object
// content, so it only requires s3:ListBucket. It falls back to the local
// JSON file source for the "local"/"test"/"integration-test" profiles. It is
// a faithful port of Java's S3TogglesSource.
package s3toggles

import (
	"context"
	"fmt"
	"os"
	"strings"

	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/s3"

	ottoconfig "github.com/otto-de/otto-config/go"
	"github.com/otto-de/otto-config/go/domain"
	"github.com/otto-de/otto-config/go/source/file"
)

const (
	onPrefix  = "on."
	offPrefix = "off."
)

// Source loads domain.Toggles from S3 object key names under a bucket
// prefix.
type Source struct {
	*ottoconfig.CachedSource

	client        *s3.Client
	bucketName    string
	togglesFolder string
}

var _ ottoconfig.Source = (*Source)(nil)

// New creates an S3-toggles-backed Source. togglesFolder is normalised to
// end with "/" (unless empty) so that it only matches its own prefix, not
// e.g. a sibling "<togglesFolder>-archive/".
func New(client *s3.Client, bucketName, togglesFolder string) *Source {
	s := &Source{client: client, bucketName: bucketName, togglesFolder: asS3Folder(togglesFolder)}
	s.CachedSource = ottoconfig.NewCachedSource(s.load, func() ottoconfig.RawConfig { return domain.EmptyToggles() })
	return s
}

func asS3Folder(location string) string {
	if location == "" || strings.HasSuffix(location, "/") {
		return location
	}
	return location + "/"
}

// Kind returns domain.TogglesKind.
func (s *Source) Kind() string { return domain.TogglesKind }

func (s *Source) load() (ottoconfig.RawConfig, error) {
	toggles := make(map[string]bool)

	paginator := s3.NewListObjectsV2Paginator(s.client, &s3.ListObjectsV2Input{
		Bucket: &s.bucketName,
		Prefix: &s.togglesFolder,
	})

	for paginator.HasMorePages() {
		page, err := paginator.NextPage(context.Background())
		if err != nil {
			return domain.EmptyToggles(), fmt.Errorf("could not load otto config toggles from S3: %w", err)
		}
		for _, obj := range page.Contents {
			if obj.Key == nil {
				continue
			}
			name, enabled, ok := parseToggleKey(*obj.Key)
			if !ok {
				continue
			}
			toggles[name] = toggles[name] || enabled
		}
	}

	return domain.NewToggles(toggles), nil
}

// parseToggleKey interprets an S3 object key's file name as a toggle:
// on.<name> / off.<name> (case-insensitive prefix). ok is false if the key
// does not follow this convention.
func parseToggleKey(key string) (name string, enabled, ok bool) {
	fileName := key
	if i := strings.LastIndex(key, "/"); i != -1 {
		fileName = key[i+1:]
	}
	lower := strings.ToLower(fileName)

	switch {
	case strings.HasPrefix(lower, onPrefix):
		name = fileName[len(onPrefix):]
		enabled = true
	case strings.HasPrefix(lower, offPrefix):
		name = fileName[len(offPrefix):]
		enabled = false
	default:
		return "", false, false
	}
	if name == "" {
		return "", false, false
	}
	return name, enabled, true
}

func init() {
	ottoconfig.RegisterSourceFactory("aws.s3.toggles", func(ctx *ottoconfig.Context) (ottoconfig.Source, error) {
		if file.ShouldFallback(ctx, os.DirFS("."), file.DefaultFileName) {
			return file.NewToggles(os.DirFS("."), file.DefaultFileName, "toggles"), nil
		}

		bucketName, ok := ottoconfig.GetValueAsString(ctx.Configuration(), "otto.config.aws.s3.toggles.bucket.name")
		if !ok || bucketName == "" {
			return nil, fmt.Errorf("missing required configuration key: otto.config.aws.s3.toggles.bucket.name")
		}
		togglesFolder, ok := ottoconfig.GetValueAsString(ctx.Configuration(), "otto.config.aws.s3.toggles.folder.name")
		if !ok || togglesFolder == "" {
			return nil, fmt.Errorf("missing required configuration key: otto.config.aws.s3.toggles.folder.name")
		}

		cfg, err := awsconfig.LoadDefaultConfig(context.Background())
		if err != nil {
			return nil, err
		}
		client := ottoconfig.GetOrRegisterClient(ctx.ClientRegistry(), func() *s3.Client {
			// Force path-style addressing (http://endpoint/bucket/key instead of
			// http://bucket.endpoint/key). Virtual-hosted-style requires the
			// bucket subdomain to be DNS-resolvable, which real AWS S3 endpoints
			// are but local S3-compatible stacks (moto, LocalStack) generally
			// are not -- that fails silently as a slow/hanging DNS lookup rather
			// than a clean connection error. Path-style is fully supported by
			// real AWS S3 for this source's simple ListObjectsV2 use case, so
			// this is safe to force unconditionally.
			return s3.NewFromConfig(cfg, func(o *s3.Options) { o.UsePathStyle = true })
		})

		return New(client, bucketName, togglesFolder), nil
	})
}
