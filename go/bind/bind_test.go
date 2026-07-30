package bind

import (
	"sync"
	"testing"

	ottoconfig "github.com/otto-de/otto-config/go"
	"github.com/otto-de/otto-config/go/domain"
)

type Nested struct {
	City string `config:"city"`
}

type Target struct {
	Name     string   `config:"name"`
	Port     int      `config:"port"`
	Enabled  bool     `config:"enabled"`
	Ratio    float64  `config:"ratio"`
	Tags     []string `config:"tags"`
	WithDef  string   `config:"missing.key,default=fallback"`
	Ignored  string   `config:"-"`
	Untagged string
	Address  Nested
}

// testPropertiesSource is a minimal, mutable ottoconfig.Source used to
// exercise Register's aggregation of real source data (as opposed to a
// Context's static bootstrap Configuration).
type testPropertiesSource struct {
	*ottoconfig.CachedSource
	mu     sync.Mutex
	values map[string]string
}

func newTestPropertiesSource(values map[string]string) *testPropertiesSource {
	s := &testPropertiesSource{values: values}
	s.CachedSource = ottoconfig.NewCachedSource(s.load, func() ottoconfig.RawConfig { return domain.EmptyProperties() })
	return s
}

func (s *testPropertiesSource) load() (ottoconfig.RawConfig, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	values := make(map[string]string, len(s.values))
	for k, v := range s.values {
		values[k] = v
	}
	return domain.NewProperties(values), nil
}

func (s *testPropertiesSource) setValues(values map[string]string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.values = values
}

func (s *testPropertiesSource) Kind() string { return domain.PropertiesKind }

func TestBind_PopulatesTaggedFields(t *testing.T) {
	cfg := ottoconfig.NewCacheWithProperties(map[string]string{
		"name":    "otto",
		"port":    "8080",
		"enabled": "true",
		"ratio":   "1.5",
		"tags":    "a, b ,c",
		"city":    "Hamburg",
	})

	var target Target
	target.Ignored = "keep-me"
	if err := Bind(cfg, &target); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if target.Name != "otto" {
		t.Errorf("Name: expected otto, got %q", target.Name)
	}
	if target.Port != 8080 {
		t.Errorf("Port: expected 8080, got %d", target.Port)
	}
	if !target.Enabled {
		t.Error("Enabled: expected true")
	}
	if target.Ratio != 1.5 {
		t.Errorf("Ratio: expected 1.5, got %v", target.Ratio)
	}
	if len(target.Tags) != 3 || target.Tags[0] != "a" || target.Tags[1] != "b" || target.Tags[2] != "c" {
		t.Errorf("Tags: expected [a b c], got %v", target.Tags)
	}
	if target.WithDef != "fallback" {
		t.Errorf("WithDef: expected fallback default, got %q", target.WithDef)
	}
	if target.Ignored != "keep-me" {
		t.Errorf("Ignored (tag \"-\"): expected untouched value keep-me, got %q", target.Ignored)
	}
	if target.Address.City != "Hamburg" {
		t.Errorf("nested Address.City: expected Hamburg, got %q", target.Address.City)
	}
}

func TestBind_RequiresPointerToStruct(t *testing.T) {
	cfg := ottoconfig.NewCacheWithProperties(map[string]string{})

	var target Target
	if err := Bind(cfg, target); err == nil {
		t.Fatal("expected error when target is not a pointer")
	}

	var s string
	if err := Bind(cfg, &s); err == nil {
		t.Fatal("expected error when target does not point to a struct")
	}
}

func TestRegister_RebindsOnRefresh(t *testing.T) {
	ctx, err := ottoconfig.NewContext("test-app", ottoconfig.WithProfile("integration-test"))
	if err != nil {
		t.Fatalf("unexpected error creating context: %v", err)
	}
	src := newTestPropertiesSource(map[string]string{"name": "first"})
	ctx.SourceRegistry().Register(src)

	var target Target
	if _, err := Register(ctx, "target", &target); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if target.Name != "first" {
		t.Fatalf("expected initial bind to set Name=first, got %q", target.Name)
	}

	src.setValues(map[string]string{"name": "second"})
	ctx.Refresh()

	if target.Name != "second" {
		t.Fatalf("expected refresh to re-bind Name=second, got %q", target.Name)
	}
}
