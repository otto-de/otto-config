package ottoconfig

import (
	"errors"
	"testing"
)

// fakeSource is a minimal ottoconfig.Source implementation for testing
// Aggregate/SourceRegistry/CachedSource without any external dependency.
type fakeSource struct {
	*CachedSource
	kind       string
	hasSecrets bool
}

func newFakeSource(kind string, hasSecrets bool, values map[string]any, loadErr error) *fakeSource {
	f := &fakeSource{kind: kind, hasSecrets: hasSecrets}
	f.CachedSource = NewCachedSource(func() (RawConfig, error) {
		if loadErr != nil {
			return nil, loadErr
		}
		return fakeRawConfig(values), nil
	}, func() RawConfig { return fakeRawConfig(nil) })
	return f
}

func (f *fakeSource) Kind() string     { return f.kind }
func (f *fakeSource) HasSecrets() bool { return f.hasSecrets }

type fakeRawConfig map[string]any

func (f fakeRawConfig) Values() map[string]any { return f }
func (f fakeRawConfig) IsEmpty() bool          { return len(f) == 0 }
func (f fakeRawConfig) Kind() string           { return "fake" }

func TestCachedSource_GetOrLoad_CachesAndForceReloads(t *testing.T) {
	calls := 0
	cs := NewCachedSource(func() (RawConfig, error) {
		calls++
		return fakeRawConfig{"k": calls}, nil
	}, func() RawConfig { return fakeRawConfig(nil) })

	first := cs.GetOrLoad(false)
	second := cs.GetOrLoad(false) // should hit cache, not reload
	if calls != 1 {
		t.Fatalf("expected 1 load call, got %d", calls)
	}
	if first.Values()["k"] != second.Values()["k"] {
		t.Fatal("expected cached value to be reused without forceReload")
	}

	cs.GetOrLoad(true) // forceReload
	if calls != 2 {
		t.Fatalf("expected 2 load calls after forceReload, got %d", calls)
	}
}

func TestCachedSource_GetOrLoad_FallsBackToCacheOnError(t *testing.T) {
	first := true
	cs := NewCachedSource(func() (RawConfig, error) {
		if first {
			first = false
			return fakeRawConfig{"k": "good"}, nil
		}
		return nil, errors.New("boom")
	}, func() RawConfig { return fakeRawConfig(nil) })

	cs.GetOrLoad(false)
	got := cs.GetOrLoad(true) // errors, should fall back to last-known-good
	if got.Values()["k"] != "good" {
		t.Fatalf("expected fallback to last-known-good cache, got %v", got.Values())
	}
}

func TestCachedSource_GetOrLoad_EmptyWhenNeverLoaded(t *testing.T) {
	cs := NewCachedSource(func() (RawConfig, error) {
		return nil, errors.New("always fails")
	}, func() RawConfig { return fakeRawConfig(nil) })

	got := cs.GetOrLoad(true)
	if !got.IsEmpty() {
		t.Fatal("expected empty RawConfig when load always fails and cache was never populated")
	}
}

func TestAggregate_FirstWriteWins(t *testing.T) {
	s1 := newFakeSource(PropertiesKindForTest, false, map[string]any{"k": "from-s1"}, nil)
	s2 := newFakeSource(PropertiesKindForTest, false, map[string]any{"k": "from-s2"}, nil)

	result := Aggregate([]Source{s1, s2}, func(v any) string { return v.(string) }, false, true, false)
	if result["k"] != "from-s1" {
		t.Fatalf("expected first source to win, got %q", result["k"])
	}
}

func TestAggregate_ExcludeSecrets(t *testing.T) {
	secretSource := newFakeSource(PropertiesKindForTest, true, map[string]any{"secret": "shh"}, nil)
	plainSource := newFakeSource(PropertiesKindForTest, false, map[string]any{"plain": "ok"}, nil)

	result := Aggregate([]Source{secretSource, plainSource}, func(v any) string { return v.(string) }, false, true, true)
	if _, ok := result["secret"]; ok {
		t.Fatal("expected secret-bearing source's values to be excluded")
	}
	if result["plain"] != "ok" {
		t.Fatal("expected non-secret value to be present")
	}
}

func TestAggregate_NormalizeKeys(t *testing.T) {
	s := newFakeSource(PropertiesKindForTest, false, map[string]any{"my/key_name": "v"}, nil)
	result := Aggregate([]Source{s}, func(v any) string { return v.(string) }, true, true, false)
	if result["my.key.name"] != "v" {
		t.Fatalf("expected normalized key my.key.name to be present, got %v", result)
	}
}

func TestSourceRegistry_FilterByKind(t *testing.T) {
	props := newFakeSource(PropertiesKindForTest, false, nil, nil)
	toggles := newFakeSource(TogglesKindForTest, false, nil, nil)

	reg := NewSourceRegistry(props, toggles)
	filtered := reg.FilterByKind([]string{PropertiesKindForTest})
	if len(filtered) != 1 || filtered[0] != Source(props) {
		t.Fatalf("expected only the properties-kind source, got %v", filtered)
	}
}

// Local kind constants to avoid importing the domain package (which would
// create an import cycle back into this package's tests).
const (
	PropertiesKindForTest = "properties"
	TogglesKindForTest    = "toggles"
)
