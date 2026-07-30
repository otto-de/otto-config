package domain

import "encoding/json"

// TogglesKind identifies Toggles as a RawConfig.Kind().
const TogglesKind = "toggles"

// Toggles is a set of named boolean feature flags (e.g. loaded from AWS
// AppConfig feature flags, AWS S3 toggle markers, or a local file).
type Toggles struct {
	properties map[string]bool
}

// NewToggles creates a Toggles from values. The supplied map is copied.
func NewToggles(values map[string]bool) *Toggles {
	t := &Toggles{properties: make(map[string]bool, len(values))}
	for k, v := range values {
		t.properties[k] = v
	}
	return t
}

// EmptyToggles returns an empty Toggles instance.
func EmptyToggles() *Toggles {
	return NewToggles(nil)
}

// BoolValues returns a copy of the underlying bool-valued map.
func (t *Toggles) BoolValues() map[string]bool {
	out := make(map[string]bool, len(t.properties))
	for k, v := range t.properties {
		out[k] = v
	}
	return out
}

// Values returns all key/value pairs, with values boxed as any.
func (t *Toggles) Values() map[string]any {
	out := make(map[string]any, len(t.properties))
	for k, v := range t.properties {
		out[k] = v
	}
	return out
}

// IsEmpty reports whether there are no toggles at all.
func (t *Toggles) IsEmpty() bool {
	return len(t.properties) == 0
}

// Kind returns TogglesKind.
func (t *Toggles) Kind() string {
	return TogglesKind
}

// UnmarshalJSON deserializes the AWS AppConfig feature-flag style JSON shape
// used by Java's Toggles: {"toggleName": {"enabled": true}, ...}.
func (t *Toggles) UnmarshalJSON(data []byte) error {
	var raw map[string]map[string]any
	if err := json.Unmarshal(data, &raw); err != nil {
		return err
	}
	properties := make(map[string]bool, len(raw))
	for key, value := range raw {
		if enabled, ok := value["enabled"].(bool); ok {
			properties[key] = enabled
		}
	}
	t.properties = properties
	return nil
}

// MarshalJSON serializes Toggles back into the {"name": {"enabled": bool}}
// shape.
func (t *Toggles) MarshalJSON() ([]byte, error) {
	out := make(map[string]map[string]bool, len(t.properties))
	for k, v := range t.properties {
		out[k] = map[string]bool{"enabled": v}
	}
	return json.Marshal(out)
}
