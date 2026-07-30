package ottoconfig

import "github.com/otto-de/otto-config/go/domain"

// combinedPropertiesSource merges several Properties-kind Sources into a
// single Source, force-reloading every child on each load and unioning
// their properties. It mirrors Java's CombinedPropertySource, which is used
// to combine per-ARN/per-path Secrets Manager, SSM, and Vault sources.
type combinedPropertiesSource struct {
	*CachedSource
	sources []Source
}

var _ Source = (*combinedPropertiesSource)(nil)

// NewCombinedPropertiesSource combines sources (which must each produce
// domain.Properties) into a single Source.
func NewCombinedPropertiesSource(sources []Source) Source {
	c := &combinedPropertiesSource{sources: sources}
	c.CachedSource = NewCachedSource(c.load, func() RawConfig { return domain.EmptyProperties() })
	return c
}

func (c *combinedPropertiesSource) Kind() string {
	return domain.PropertiesKind
}

func (c *combinedPropertiesSource) HasSecrets() bool {
	for _, s := range c.sources {
		if s.HasSecrets() {
			return true
		}
	}
	return false
}

func (c *combinedPropertiesSource) OnChanged(event ChangeEvent) bool {
	changed := false
	for _, s := range c.sources {
		if s.OnChanged(event) {
			changed = true
		}
	}
	return changed
}

func (c *combinedPropertiesSource) load() (RawConfig, error) {
	combined := make(map[string]string)
	for _, s := range c.sources {
		raw := s.GetOrLoad(true)
		for k, v := range raw.Values() {
			if str, ok := v.(string); ok {
				combined[k] = str
			}
		}
	}
	return domain.NewProperties(combined), nil
}
