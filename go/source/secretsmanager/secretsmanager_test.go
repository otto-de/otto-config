package secretsmanager

import (
	"testing"

	"github.com/otto-de/otto-config/go/event"
)

func TestMergeEntriesAsListValues_GroupsAndJoins(t *testing.T) {
	entries := []entry{
		{key: "user_AWSCURRENT", value: "alice"},
		{key: "user_AWSPREVIOUS", value: "bob"},
		{key: "password_AWSCURRENT", value: "secret1"},
	}

	result := mergeEntriesAsListValues(entries, func(key string) string {
		return versionSuffixPattern.ReplaceAllString(key, "")
	})

	if result["user"] != "alice,bob" {
		t.Fatalf("expected user=\"alice,bob\", got %q", result["user"])
	}
	if result["password"] != "secret1" {
		t.Fatalf("expected password=\"secret1\", got %q", result["password"])
	}
}

func TestMergeEntriesAsListValues_SortsValuesWithinGroup(t *testing.T) {
	entries := []entry{
		{key: "user_AWSPREVIOUS", value: "zebra"},
		{key: "user_AWSCURRENT", value: "alpha"},
	}

	result := mergeEntriesAsListValues(entries, func(key string) string {
		return versionSuffixPattern.ReplaceAllString(key, "")
	})

	// Entries are sorted by (key, value) before grouping, so AWSCURRENT
	// (alphabetically before AWSPREVIOUS) sorts first regardless of input
	// order.
	if result["user"] != "alpha,zebra" {
		t.Fatalf("expected sorted \"alpha,zebra\", got %q", result["user"])
	}
}

func TestSource_OnChanged_MatchesByArnOrName(t *testing.T) {
	s := New(nil, "arn:aws:secretsmanager:eu-central-1:123456789012:secret:otto-config-abcdef", true)

	if !s.OnChanged(event.SecretsManagerChangeEvent{SecretID: "otto-config"}) {
		t.Fatal("expected a bare secret name contained in the full ARN to match")
	}
	if s.OnChanged(event.SecretsManagerChangeEvent{SecretID: "some-unrelated-secret"}) {
		t.Fatal("expected an unrelated secret id to not match")
	}
}

func TestSource_OnChanged_IgnoresOtherEventTypes(t *testing.T) {
	s := New(nil, "otto-config", true)

	if s.OnChanged(event.SsmParameterChangeEvent{ParameterName: "/otto-config/x"}) {
		t.Fatal("expected a non-SecretsManagerChangeEvent to never match")
	}
}
