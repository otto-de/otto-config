package ottoconfig

import "testing"

func TestGetValueAsString(t *testing.T) {
	c := NewCacheWithProperties(map[string]string{"k": "v"})
	if got, ok := GetValueAsString[string](c, "k"); !ok || got != "v" {
		t.Fatalf("expected (v, true), got (%q, %v)", got, ok)
	}
	if got := GetValueAsStringOr[string](c, "missing", "def"); got != "def" {
		t.Fatalf("expected def, got %q", got)
	}
}

func TestGetValueAsInt(t *testing.T) {
	c := NewCacheWithProperties(map[string]string{"k": "5"})
	if got := GetValueAsInt[string](c, "k", -1); got != 5 {
		t.Fatalf("expected 5, got %d", got)
	}
	if got := GetValueAsInt[string](c, "missing", -1); got != -1 {
		t.Fatalf("expected default -1, got %d", got)
	}
	c2 := NewCacheWithProperties(map[string]string{"k": "not-an-int"})
	if got := GetValueAsInt[string](c2, "k", -1); got != -1 {
		t.Fatalf("expected default -1 for unparsable int, got %d", got)
	}
}

func TestGetValueAsBool(t *testing.T) {
	c := NewCacheWithProperties(map[string]string{"k": "true"})
	if got := GetValueAsBool[string](c, "k", false); !got {
		t.Fatal("expected true")
	}
	if got := GetValueAsBool[string](c, "missing", true); !got {
		t.Fatal("expected default true for missing key")
	}
	c2 := NewCacheWithProperties(map[string]string{"k": "not-a-bool"})
	if got := GetValueAsBool[string](c2, "k", true); !got {
		t.Fatal("expected default true for unparsable bool")
	}
}
