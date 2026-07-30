package ottoconfig

// PropertyRegistry stores named Property[T]/RefreshableProperty[T]/
// PropertyVersion instances (as `any`, since they are generic over
// different T) and cascades Refresh/RefreshInPlace to any of them that
// implement Refreshable. It mirrors Java's PropertyRegistry.
type PropertyRegistry struct {
	*MapRegistry[string, any]
}

// NewPropertyRegistry creates an empty PropertyRegistry.
func NewPropertyRegistry() *PropertyRegistry {
	return &PropertyRegistry{MapRegistry: NewMapRegistry[string, any]()}
}

// Refresh calls Refresh() on every registered value that implements
// Refreshable.
func (r *PropertyRegistry) Refresh() {
	for _, v := range r.Values() {
		if refreshable, ok := v.(Refreshable); ok {
			refreshable.Refresh()
		}
	}
}

// RefreshInPlace calls RefreshInPlace() (falling back to Refresh()) on every
// registered value that implements Refreshable.
func (r *PropertyRegistry) RefreshInPlace() {
	for _, v := range r.Values() {
		if refreshable, ok := v.(Refreshable); ok {
			refreshInPlace(refreshable)
		}
	}
}

var _ Refreshable = (*PropertyRegistry)(nil)

// ProviderRegistry holds all registered Provider[T] instances (stored as
// Refreshable, since Provider is generic over different T) and cascades
// Refresh/RefreshInPlace to all of them. It mirrors Java's ProviderRegistry.
type ProviderRegistry struct {
	*ListRegistry[Refreshable]
}

// NewProviderRegistry creates an empty ProviderRegistry.
func NewProviderRegistry() *ProviderRegistry {
	return &ProviderRegistry{ListRegistry: NewListRegistry[Refreshable]()}
}

// Refresh calls Refresh() on every registered provider.
func (r *ProviderRegistry) Refresh() {
	for _, p := range r.Values() {
		p.Refresh()
	}
}

// RefreshInPlace calls RefreshInPlace() (falling back to Refresh()) on every
// registered provider.
func (r *ProviderRegistry) RefreshInPlace() {
	for _, p := range r.Values() {
		refreshInPlace(p)
	}
}

var _ Refreshable = (*ProviderRegistry)(nil)
