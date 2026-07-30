package ottoconfig

import "sync"

// ListRegistry is a thread-safe, ordered list of values. It mirrors Java's
// ListRegistry<T>.
type ListRegistry[T any] struct {
	mu     sync.RWMutex
	values []T
}

// NewListRegistry creates a ListRegistry pre-populated with values.
func NewListRegistry[T any](values ...T) *ListRegistry[T] {
	r := &ListRegistry[T]{}
	r.values = append(r.values, values...)
	return r
}

// Register appends value to the registry.
func (r *ListRegistry[T]) Register(value T) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.values = append(r.values, value)
}

// Values returns a snapshot copy of all registered values.
func (r *ListRegistry[T]) Values() []T {
	r.mu.RLock()
	defer r.mu.RUnlock()
	out := make([]T, len(r.values))
	copy(out, r.values)
	return out
}

// Clear removes all registered values.
func (r *ListRegistry[T]) Clear() {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.values = nil
}

// MapRegistry is a thread-safe map of keyed values. It mirrors Java's
// MapRegistry<K, V>.
type MapRegistry[K comparable, V any] struct {
	mu     sync.RWMutex
	values map[K]V
}

// NewMapRegistry creates an empty MapRegistry.
func NewMapRegistry[K comparable, V any]() *MapRegistry[K, V] {
	return &MapRegistry[K, V]{values: make(map[K]V)}
}

// Register stores value under key, overwriting any existing entry.
func (r *MapRegistry[K, V]) Register(key K, value V) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.values[key] = value
}

// RegisterIfAbsent returns the existing value for key if present, otherwise
// calls create, stores, and returns the new value.
func (r *MapRegistry[K, V]) RegisterIfAbsent(key K, create func() V) V {
	r.mu.Lock()
	defer r.mu.Unlock()
	if v, ok := r.values[key]; ok {
		return v
	}
	v := create()
	r.values[key] = v
	return v
}

// Unregister removes key from the registry.
func (r *MapRegistry[K, V]) Unregister(key K) {
	r.mu.Lock()
	defer r.mu.Unlock()
	delete(r.values, key)
}

// Contains reports whether key is present.
func (r *MapRegistry[K, V]) Contains(key K) bool {
	r.mu.RLock()
	defer r.mu.RUnlock()
	_, ok := r.values[key]
	return ok
}

// Get returns the value stored for key, and whether it was found.
func (r *MapRegistry[K, V]) Get(key K) (V, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	v, ok := r.values[key]
	return v, ok
}

// Values returns a snapshot copy of all registered values.
func (r *MapRegistry[K, V]) Values() []V {
	r.mu.RLock()
	defer r.mu.RUnlock()
	out := make([]V, 0, len(r.values))
	for _, v := range r.values {
		out = append(out, v)
	}
	return out
}

// Clear removes all entries.
func (r *MapRegistry[K, V]) Clear() {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.values = make(map[K]V)
}
