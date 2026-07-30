package s3toggles

import "testing"

func TestParseToggleKey_OnPrefix(t *testing.T) {
	name, enabled, ok := parseToggleKey("feature-toggles/on.myFeature")
	if !ok || name != "myFeature" || !enabled {
		t.Fatalf("expected (myFeature, true, true), got (%q, %v, %v)", name, enabled, ok)
	}
}

func TestParseToggleKey_OffPrefix(t *testing.T) {
	name, enabled, ok := parseToggleKey("feature-toggles/off.myFeature")
	if !ok || name != "myFeature" || enabled {
		t.Fatalf("expected (myFeature, false, true), got (%q, %v, %v)", name, enabled, ok)
	}
}

func TestParseToggleKey_CaseInsensitivePrefix(t *testing.T) {
	name, enabled, ok := parseToggleKey("ON.myFeature")
	if !ok || name != "myFeature" || !enabled {
		t.Fatalf("expected case-insensitive ON. prefix to match, got (%q, %v, %v)", name, enabled, ok)
	}
}

func TestParseToggleKey_NoRecognizedPrefix(t *testing.T) {
	if _, _, ok := parseToggleKey("feature-toggles/readme.txt"); ok {
		t.Fatal("expected ok=false for a key without on./off. prefix")
	}
}

func TestParseToggleKey_EmptyName(t *testing.T) {
	if _, _, ok := parseToggleKey("on."); ok {
		t.Fatal("expected ok=false for an empty toggle name")
	}
}

func TestAsS3Folder(t *testing.T) {
	cases := map[string]string{
		"":                 "",
		"feature-toggles":  "feature-toggles/",
		"feature-toggles/": "feature-toggles/",
	}
	for in, want := range cases {
		if got := asS3Folder(in); got != want {
			t.Errorf("asS3Folder(%q) = %q, want %q", in, got, want)
		}
	}
}
