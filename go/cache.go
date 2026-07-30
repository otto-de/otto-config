package ottoconfig

import "sync"

// Cache is a thread-safe, mutable implementation of Configuration[T]. It
// mirrors Java's ConfigurationCache<T>: exact-match lookups fall back to
// relaxed-binding name variants (camelCase, kebab-case, snake_case, etc.) so
// that keys can be read regardless of the naming convention used to set
// them.
type Cache[T any] struct {
	mu         sync.RWMutex
	properties map[string]T
}

// NewCache creates an empty Cache[T].
func NewCache[T any]() *Cache[T] {
	return &Cache[T]{properties: make(map[string]T)}
}

// NewCacheWithProperties creates a Cache[T] pre-populated with properties.
// The supplied map is copied.
func NewCacheWithProperties[T any](properties map[string]T) *Cache[T] {
	c := NewCache[T]()
	c.SetProperties(properties)
	return c
}

// GetValue returns the value for key, first trying an exact match and then
// falling back to relaxed-binding name variants.
func (c *Cache[T]) GetValue(key string) (T, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()

	if v, ok := c.properties[key]; ok {
		return v, true
	}
	for _, variant := range GenerateVariants(key) {
		if v, ok := c.properties[variant]; ok {
			return v, true
		}
	}
	var zero T
	return zero, false
}

// GetValueOr returns the value for key, or def if key was not found.
func (c *Cache[T]) GetValueOr(key string, def T) T {
	if v, ok := c.GetValue(key); ok {
		return v
	}
	return def
}

// Properties returns a copy of all key/value pairs currently stored.
func (c *Cache[T]) Properties() map[string]T {
	c.mu.RLock()
	defer c.mu.RUnlock()

	out := make(map[string]T, len(c.properties))
	for k, v := range c.properties {
		out[k] = v
	}
	return out
}

// SetProperties atomically replaces all stored properties.
func (c *Cache[T]) SetProperties(values map[string]T) {
	c.mu.Lock()
	defer c.mu.Unlock()

	properties := make(map[string]T, len(values))
	for k, v := range values {
		properties[k] = v
	}
	c.properties = properties
}

// PropertyNames returns the set of exact keys currently stored.
func (c *Cache[T]) PropertyNames() []string {
	c.mu.RLock()
	defer c.mu.RUnlock()

	names := make([]string, 0, len(c.properties))
	for k := range c.properties {
		names = append(names, k)
	}
	return names
}

// ContainsKey reports whether key (or a relaxed-binding variant) is present.
func (c *Cache[T]) ContainsKey(key string) bool {
	_, ok := c.GetValue(key)
	return ok
}

// IsEmpty reports whether the cache holds no properties.
func (c *Cache[T]) IsEmpty() bool {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return len(c.properties) == 0
}

var _ Configuration[string] = (*Cache[string])(nil)
