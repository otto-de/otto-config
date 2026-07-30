package ottoconfig

import "testing"

func TestCache_RelaxedBinding(t *testing.T) {
	c := NewCacheWithProperties(map[string]string{"myKey.someName": "value"})

	// Exact match.
	if v, ok := c.GetValue("myKey.someName"); !ok || v != "value" {
		t.Fatalf("expected exact match to succeed, got (%q, %v)", v, ok)
	}

	// Relaxed binding: looking up the kebab-case form should still resolve
	// to the stored camelCase key, via GenerateVariants reconstructing
	// "myKey.someName" as one of the lookup key's variants.
	if v, ok := c.GetValue("my-key.some-name"); !ok || v != "value" {
		t.Fatalf("expected relaxed-binding kebab-case match to succeed, got (%q, %v)", v, ok)
	}
}

func TestCache_GetValueOr(t *testing.T) {
	c := NewCacheWithProperties(map[string]string{"k": "v"})
	if got := c.GetValueOr("k", "default"); got != "v" {
		t.Fatalf("expected %q, got %q", "v", got)
	}
	if got := c.GetValueOr("missing", "default"); got != "default" {
		t.Fatalf("expected default, got %q", got)
	}
}

func TestCache_SetPropertiesReplacesAll(t *testing.T) {
	c := NewCacheWithProperties(map[string]string{"a": "1"})
	c.SetProperties(map[string]string{"b": "2"})

	if _, ok := c.GetValue("a"); ok {
		t.Fatal("expected \"a\" to be gone after SetProperties replaced the map")
	}
	if v, ok := c.GetValue("b"); !ok || v != "2" {
		t.Fatalf("expected (2, true), got (%q, %v)", v, ok)
	}
}

func TestCache_ContainsKeyAndIsEmpty(t *testing.T) {
	empty := NewCache[string]()
	if !empty.IsEmpty() {
		t.Fatal("expected new cache to be empty")
	}

	c := NewCacheWithProperties(map[string]string{"k": "v"})
	if c.IsEmpty() {
		t.Fatal("expected populated cache to not be empty")
	}
	if !c.ContainsKey("k") {
		t.Fatal("expected ContainsKey(\"k\") to be true")
	}
	if c.ContainsKey("missing") {
		t.Fatal("expected ContainsKey(\"missing\") to be false")
	}
}
