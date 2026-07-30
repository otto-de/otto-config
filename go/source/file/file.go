// Package file provides a local, JSON-file-backed Source implementation
// used both directly (for local/test development) and as the fallback
// backend for the AWS/Vault sources when the active Context profile is
// "local", "test", or "integration-test" and the fallback file is present.
// It mirrors Java's de.otto.config.source.file.FileSource plus the
// isLocalProfile/isLocalFileSourceAvailable helpers from CoreSourceFactory.
package file

import (
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"

	ottoconfig "github.com/otto-de/otto-config/go"
	"github.com/otto-de/otto-config/go/domain"
)

// DefaultFileName is the conventional local fallback file name, mirroring
// Java's "properties.json" classpath resource. It contains a JSON object
// with "properties" and/or "toggles" sections, e.g.:
//
//	{
//	  "properties": {"key": "value"},
//	  "toggles": {"flagName": {"enabled": true}}
//	}
const DefaultFileName = "properties.json"

// Available reports whether file can be opened for reading within fsys.
func Available(fsys fs.FS, file string) bool {
	f, err := fsys.Open(file)
	if err != nil {
		return false
	}
	_ = f.Close()
	return true
}

// ShouldFallback reports whether ctx's profile is "local", "test",
// "integration-test" (or unset) AND the fallback file is available. AWS/
// Vault source factories call this to decide whether to bypass their
// external backend entirely, mirroring Java's
// CoreSourceFactory.isLocalProfile.
func ShouldFallback(ctx *ottoconfig.Context, fsys fs.FS, file string) bool {
	return ctx.IsLocalProfile() && Available(fsys, file)
}

// Source loads RawConfig from a JSON file, optionally extracting a named
// top-level section (e.g. "properties" or "toggles"). A missing file or
// missing section both resolve to the empty value rather than an error,
// mirroring Java's FileSource.
type Source struct {
	*ottoconfig.CachedSource

	fsys      fs.FS
	file      string
	section   string
	kind      string
	unmarshal func([]byte) (ottoconfig.RawConfig, error)
}

// New creates a file-backed Source reading file from fsys. If section is
// non-empty, only that top-level JSON field is parsed (via unmarshal);
// otherwise the whole file is parsed directly.
func New(
	fsys fs.FS,
	file, section, kind string,
	unmarshal func([]byte) (ottoconfig.RawConfig, error),
	empty func() ottoconfig.RawConfig,
) *Source {
	s := &Source{fsys: fsys, file: file, section: section, kind: kind, unmarshal: unmarshal}
	s.CachedSource = ottoconfig.NewCachedSource(s.load, empty)
	return s
}

// Kind returns this Source's RawConfig kind (e.g. "properties", "toggles").
func (s *Source) Kind() string { return s.kind }

func (s *Source) load() (ottoconfig.RawConfig, error) {
	data, err := fs.ReadFile(s.fsys, s.file)
	if err != nil {
		if errors.Is(err, fs.ErrNotExist) {
			return s.CachedSource.Empty(), nil
		}
		return nil, fmt.Errorf("reading %s: %w", s.file, err)
	}

	if s.section != "" {
		var root map[string]json.RawMessage
		if err := json.Unmarshal(data, &root); err != nil {
			return nil, fmt.Errorf("parsing %s: %w", s.file, err)
		}
		sectionData, ok := root[s.section]
		if !ok {
			return s.CachedSource.Empty(), nil
		}
		data = sectionData
	}

	return s.unmarshal(data)
}

var _ ottoconfig.Source = (*Source)(nil)

// NewProperties creates a file-backed Source producing domain.Properties.
func NewProperties(fsys fs.FS, file, section string) *Source {
	return New(fsys, file, section, domain.PropertiesKind, unmarshalProperties, func() ottoconfig.RawConfig {
		return domain.EmptyProperties()
	})
}

func unmarshalProperties(data []byte) (ottoconfig.RawConfig, error) {
	p := domain.EmptyProperties()
	if err := json.Unmarshal(data, p); err != nil {
		return nil, err
	}
	return p, nil
}

// NewToggles creates a file-backed Source producing domain.Toggles.
func NewToggles(fsys fs.FS, file, section string) *Source {
	return New(fsys, file, section, domain.TogglesKind, unmarshalToggles, func() ottoconfig.RawConfig {
		return domain.EmptyToggles()
	})
}

func unmarshalToggles(data []byte) (ottoconfig.RawConfig, error) {
	t := domain.EmptyToggles()
	if err := json.Unmarshal(data, t); err != nil {
		return nil, err
	}
	return t, nil
}
