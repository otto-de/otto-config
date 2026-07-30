package ssm

import "testing"

func TestAddServiceLevelProperty_MatchingApplication(t *testing.T) {
	s := New("my-app", nil, "/search/develop", true, false)
	properties := map[string]string{}

	s.addServiceLevelProperty(properties, "/search/develop/my-app/config/some-key", "value")

	if properties["some-key"] != "value" {
		t.Fatalf("expected bare alias \"some-key\" for the matching application, got %v", properties)
	}
}

func TestAddServiceLevelProperty_OtherService(t *testing.T) {
	s := New("my-app", nil, "/search/develop", true, false)
	properties := map[string]string{}

	s.addServiceLevelProperty(properties, "/search/develop/other-service/config/some-key", "value")

	if properties["other-service/some-key"] != "value" {
		t.Fatalf("expected \"other-service/some-key\" alias for a different service, got %v", properties)
	}
}

func TestAddServiceLevelProperty_NonLegacyFormat(t *testing.T) {
	s := New("my-app", nil, "/search/develop", true, false)
	properties := map[string]string{}

	s.addServiceLevelProperty(properties, "/search/develop/plain-key", "value")

	if len(properties) != 0 {
		t.Fatalf("expected no alias added for a non-legacy-format key, got %v", properties)
	}
}

func TestAddServiceLevelProperty_NoConfigSegment(t *testing.T) {
	s := New("my-app", nil, "/search/develop", true, false)
	properties := map[string]string{}

	// Same 5-segment shape as the legacy format, but without a literal
	// "/config/" segment, so addServiceLevelProperty should bail out early
	// via its strings.Contains(name, "/config/") guard before ever running
	// the regex.
	s.addServiceLevelProperty(properties, "/search/develop/my-app/other/some-key", "value")

	if len(properties) != 0 {
		t.Fatalf("expected no alias added without a /config/ segment, got %v", properties)
	}
}
