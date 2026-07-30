package domain

import (
	"encoding/json"
	"testing"
)

func TestToggles_UnmarshalJSON(t *testing.T) {
	var toggles Toggles
	data := []byte(`{"featureA":{"enabled":true},"featureB":{"enabled":false}}`)
	if err := json.Unmarshal(data, &toggles); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	values := toggles.BoolValues()
	if values["featureA"] != true {
		t.Errorf("expected featureA=true, got %v", values["featureA"])
	}
	if values["featureB"] != false {
		t.Errorf("expected featureB=false, got %v", values["featureB"])
	}
}

func TestToggles_MarshalJSON_RoundTrip(t *testing.T) {
	original := NewToggles(map[string]bool{"a": true, "b": false})

	data, err := json.Marshal(original)
	if err != nil {
		t.Fatalf("unexpected error marshaling: %v", err)
	}

	var roundTripped Toggles
	if err := json.Unmarshal(data, &roundTripped); err != nil {
		t.Fatalf("unexpected error unmarshaling: %v", err)
	}

	if roundTripped.BoolValues()["a"] != true || roundTripped.BoolValues()["b"] != false {
		t.Fatalf("round-trip mismatch: %v", roundTripped.BoolValues())
	}
}

func TestToggles_EmptyAndKind(t *testing.T) {
	empty := EmptyToggles()
	if !empty.IsEmpty() {
		t.Fatal("expected EmptyToggles() to be empty")
	}
	if empty.Kind() != TogglesKind {
		t.Fatalf("expected Kind()=%q, got %q", TogglesKind, empty.Kind())
	}
}
