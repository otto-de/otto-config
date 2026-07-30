package domain

import "testing"

func TestProperties_NewAndValues(t *testing.T) {
	p := NewProperties(map[string]string{"a": "1"})
	if p.IsEmpty() {
		t.Fatal("expected populated Properties to not be empty")
	}
	if p.Kind() != PropertiesKind {
		t.Fatalf("expected Kind()=%q, got %q", PropertiesKind, p.Kind())
	}
	if got := p.Values()["a"]; got != "1" {
		t.Fatalf("expected Values()[\"a\"]=\"1\", got %v", got)
	}
}

func TestProperties_CopiesInputMap(t *testing.T) {
	source := map[string]string{"a": "1"}
	p := NewProperties(source)
	source["a"] = "mutated"

	if p.StringValues()["a"] != "1" {
		t.Fatal("expected NewProperties to copy its input map, not alias it")
	}
}

func TestEmptyProperties(t *testing.T) {
	if !EmptyProperties().IsEmpty() {
		t.Fatal("expected EmptyProperties() to be empty")
	}
}
