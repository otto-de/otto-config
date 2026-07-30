package ottoconfig

import (
	"slices"
	"strings"
	"testing"
)

func TestGenerateVariants_AlreadyLowercaseNoSeparators(t *testing.T) {
	if v := GenerateVariants("mykey"); v != nil {
		t.Fatalf("expected nil for already-lowercase name, got %v", v)
	}
	if v := GenerateVariants("my.key"); v != nil {
		t.Fatalf("expected nil for already-lowercase dotted name, got %v", v)
	}
}

func TestGenerateVariants_CamelCase(t *testing.T) {
	variants := GenerateVariants("my-key_name")
	if !slices.Contains(variants, "myKeyName") {
		t.Errorf("expected camelCase variant myKeyName in %v", variants)
	}
	if !slices.Contains(variants, "my-key-name") {
		t.Errorf("expected kebab-case variant my-key-name in %v", variants)
	}
	if !slices.Contains(variants, "my_key_name") {
		t.Errorf("expected underscore variant my_key_name in %v", variants)
	}
	if !slices.Contains(variants, "MY_KEY_NAME") {
		t.Errorf("expected SCREAMING_SNAKE variant MY_KEY_NAME in %v", variants)
	}
}

func TestGenerateVariants_CamelCaseInput(t *testing.T) {
	variants := GenerateVariants("MyKeyName")
	if !slices.Contains(variants, "my-key-name") {
		t.Errorf("expected kebab-case variant my-key-name in %v", variants)
	}
}

func TestGenerateVariants_DottedSegments(t *testing.T) {
	variants := GenerateVariants("my-section.some_key")
	for _, v := range variants {
		if !strings.Contains(v, ".") {
			t.Errorf("expected variant %q to preserve the dot separator", v)
		}
	}
}
