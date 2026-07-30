package ottoconfig

import (
	"fmt"

	"github.com/otto-de/otto-config/go/domain"
)

// Provider aggregates values from a filtered subset of a Context's
// registered Sources (selected by RawConfig Kind) into a Cache[T], applying
// valueTransformer to convert each raw value to T. It mirrors Java's
// abstract Provider<T> class.
type Provider[T any] struct {
	*Cache[T]

	ctx              *Context
	valueTransformer func(any) T
	filterKinds      []string
	normalizeKeys    bool
}

// NewProvider creates a Provider[T], registers it with ctx's
// ProviderRegistry, optionally registers initial sources with ctx's
// SourceRegistry, and performs an initial Refresh.
func NewProvider[T any](
	ctx *Context,
	sources []Source,
	filterKinds []string,
	valueTransformer func(any) T,
	normalizeKeys bool,
) *Provider[T] {
	p := &Provider[T]{
		Cache:            NewCache[T](),
		ctx:              ctx,
		valueTransformer: valueTransformer,
		filterKinds:      filterKinds,
		normalizeKeys:    normalizeKeys,
	}

	ctx.ProviderRegistry().Register(p)

	if len(sources) > 0 {
		for _, source := range sources {
			ctx.SourceRegistry().Register(source)
		}
	}

	p.Refresh()
	return p
}

// AddSource registers source with the underlying Context's SourceRegistry
// and triggers a Refresh.
func (p *Provider[T]) AddSource(source Source) {
	p.ctx.SourceRegistry().Register(source)
	p.Refresh()
}

// Context returns the Context this Provider was created with.
func (p *Provider[T]) Context() *Context { return p.ctx }

// AsMap returns a copy of the aggregated properties, boxed as `any`.
func (p *Provider[T]) AsMap() map[string]any {
	props := p.Properties()
	out := make(map[string]any, len(props))
	for k, v := range props {
		out[k] = v
	}
	return out
}

// Refresh performs a full refresh: matching sources are force-reloaded and
// re-aggregated.
func (p *Provider[T]) Refresh() {
	sources := p.ctx.SourceRegistry().FilterByKind(p.filterKinds)
	p.SetProperties(Aggregate(sources, p.valueTransformer, p.normalizeKeys, true, p.ctx.ExcludeSecrets()))
}

// RefreshInPlace re-aggregates from matching sources' current caches without
// forcing a reload.
func (p *Provider[T]) RefreshInPlace() {
	sources := p.ctx.SourceRegistry().FilterByKind(p.filterKinds)
	p.SetProperties(Aggregate(sources, p.valueTransformer, p.normalizeKeys, false, p.ctx.ExcludeSecrets()))
}

var _ Refreshable = (*Provider[string])(nil)
var _ RefreshInPlacer = (*Provider[string])(nil)

// NewConfigurationProvider creates the main public entry point of the
// library: a Provider[string] that aggregates only "properties" and
// "toggles" sources, converts values to string via fmt.Sprint, and applies
// relaxed-binding key normalization. It mirrors Java's
// ConfigurationProvider.
func NewConfigurationProvider(ctx *Context, sources ...Source) *Provider[string] {
	return NewProvider[string](
		ctx,
		sources,
		[]string{domain.PropertiesKind, domain.TogglesKind},
		func(v any) string { return fmt.Sprint(v) },
		true,
	)
}
