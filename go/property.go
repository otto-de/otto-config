package ottoconfig

import "sync"

// Property is a simple, thread-safe value holder. It mirrors Java's
// Property<T>.
type Property[T any] struct {
	mu       sync.RWMutex
	value    T
	hasValue bool
}

// NewProperty creates a Property already holding value.
func NewProperty[T any](value T) *Property[T] {
	return &Property[T]{value: value, hasValue: true}
}

// Value returns the current value.
func (p *Property[T]) Value() T {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return p.value
}

// IsEmpty reports whether the property has never held a value (i.e. the
// underlying Configuration lookup found nothing). Unlike Java, which checks
// for a null value, this uses an explicit "has value" flag since Go generics
// can't compare arbitrary T against nil.
func (p *Property[T]) IsEmpty() bool {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return !p.hasValue
}

func (p *Property[T]) setValue(value T, ok bool) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.value = value
	p.hasValue = ok
}

// RefreshableProperty is a Property[T] that reloads its value from a
// Configuration[string] on Refresh(). It mirrors Java's
// RefreshableProperty<T>.
type RefreshableProperty[T any] struct {
	Property[T]

	key           string
	configuration Configuration[string]
}

// NewRefreshableProperty creates a RefreshableProperty for key, sourcing
// values from configuration, and performs an initial Refresh.
func NewRefreshableProperty[T any](key string, configuration Configuration[string]) *RefreshableProperty[T] {
	p := &RefreshableProperty[T]{key: key, configuration: configuration}
	p.Refresh()
	return p
}

// Key returns the configuration key this property tracks.
func (p *RefreshableProperty[T]) Key() string { return p.key }

// Refresh reloads the value from the underlying Configuration.
func (p *RefreshableProperty[T]) Refresh() {
	value, ok := GetValueByType[T](p.configuration, p.key)
	p.setValue(value, ok)
}

var _ Refreshable = (*RefreshableProperty[string])(nil)

// RegisterRefreshableProperty returns the RefreshableProperty[T] registered
// under key in ctx's PropertyRegistry, creating (and registering) one if
// absent. It mirrors Java's RefreshableProperty.register.
func RegisterRefreshableProperty[T any](ctx *Context, key string) *RefreshableProperty[T] {
	v := ctx.PropertyRegistry().RegisterIfAbsent(key, func() any {
		return NewRefreshableProperty[T](key, ctx.Configuration())
	})
	return v.(*RefreshableProperty[T])
}
