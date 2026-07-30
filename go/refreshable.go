package ottoconfig

// Refreshable is implemented by components that can reload their state.
type Refreshable interface {
	Refresh()
}

// RefreshInPlacer is implemented by Refreshable components that support a
// lighter-weight, non-destructive refresh (e.g. used by event-driven
// notifications where a full reload isn't necessary). Components that don't
// implement it are refreshed via a normal Refresh() call instead.
type RefreshInPlacer interface {
	Refreshable
	RefreshInPlace()
}

// refreshInPlace calls RefreshInPlace if r implements RefreshInPlacer,
// otherwise falls back to Refresh. This mirrors Java's
// Refreshable.refreshInPlace() default method.
func refreshInPlace(r Refreshable) {
	if rip, ok := r.(RefreshInPlacer); ok {
		rip.RefreshInPlace()
		return
	}
	r.Refresh()
}
