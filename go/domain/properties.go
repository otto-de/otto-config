// Package domain contains the built-in RawConfig shapes loaded by Source
// implementations: Properties (flat string key/value pairs) and Toggles
// (named boolean feature flags). They mirror
// de.otto.config.domain.{Properties,Toggles}.
//
// Both types implement ottoconfig.RawConfig structurally (Values, IsEmpty,
// Kind) without importing the root package, keeping the dependency
// one-directional (root -> domain).
package domain

import "encoding/json"

// PropertiesKind identifies Properties as a RawConfig.Kind().
const PropertiesKind = "properties"

// Properties is a flat, string-valued configuration section (e.g. loaded
// from AWS AppConfig, Secrets Manager, SSM, Vault, or a local file).
type Properties struct {
	properties map[string]string
}

// NewProperties creates a Properties from values. The supplied map is
// copied.
func NewProperties(values map[string]string) *Properties {
	p := &Properties{properties: make(map[string]string, len(values))}
	for k, v := range values {
		p.properties[k] = v
	}
	return p
}

// EmptyProperties returns an empty Properties instance.
func EmptyProperties() *Properties {
	return NewProperties(nil)
}

// StringValues returns a copy of the underlying string-valued map.
func (p *Properties) StringValues() map[string]string {
	out := make(map[string]string, len(p.properties))
	for k, v := range p.properties {
		out[k] = v
	}
	return out
}

// Values returns all key/value pairs, with values boxed as any.
func (p *Properties) Values() map[string]any {
	out := make(map[string]any, len(p.properties))
	for k, v := range p.properties {
		out[k] = v
	}
	return out
}

// IsEmpty reports whether there are no properties at all.
func (p *Properties) IsEmpty() bool {
	return len(p.properties) == 0
}

// Kind returns PropertiesKind.
func (p *Properties) Kind() string {
	return PropertiesKind
}

// UnmarshalJSON deserializes a flat JSON object of string values, e.g.
// {"myKey": "myValue"}.
func (p *Properties) UnmarshalJSON(data []byte) error {
	var raw map[string]string
	if err := json.Unmarshal(data, &raw); err != nil {
		return err
	}
	p.properties = raw
	return nil
}

// MarshalJSON serializes Properties as a flat JSON object.
func (p *Properties) MarshalJSON() ([]byte, error) {
	if p.properties == nil {
		return json.Marshal(map[string]string{})
	}
	return json.Marshal(p.properties)
}
