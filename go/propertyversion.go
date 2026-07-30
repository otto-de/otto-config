package ottoconfig

import "sync"

// PropertyVersion holds an ordered snapshot of a key's known values, most
// recent first: [current, previous, ...]. It mirrors Java's
// PropertyVersion.
type PropertyVersion struct {
	mu       sync.RWMutex
	versions []string
}

// NewPropertyVersion creates a PropertyVersion holding a copy of versions.
func NewPropertyVersion(versions []string) *PropertyVersion {
	pv := &PropertyVersion{}
	pv.setVersions(versions)
	return pv
}

func (pv *PropertyVersion) setVersions(versions []string) {
	pv.mu.Lock()
	defer pv.mu.Unlock()
	v := make([]string, len(versions))
	copy(v, versions)
	pv.versions = v
}

// Versions returns a copy of all known versions, most recent first.
func (pv *PropertyVersion) Versions() []string {
	pv.mu.RLock()
	defer pv.mu.RUnlock()
	out := make([]string, len(pv.versions))
	copy(out, pv.versions)
	return out
}

// Current returns the most recent version, if any.
func (pv *PropertyVersion) Current() (string, bool) {
	pv.mu.RLock()
	defer pv.mu.RUnlock()
	if len(pv.versions) == 0 {
		return "", false
	}
	return pv.versions[0], true
}

// Previous returns the second-most-recent version, if any.
func (pv *PropertyVersion) Previous() (string, bool) {
	pv.mu.RLock()
	defer pv.mu.RUnlock()
	if len(pv.versions) < 2 {
		return "", false
	}
	return pv.versions[1], true
}

// IsEmpty reports whether there are no known versions.
func (pv *PropertyVersion) IsEmpty() bool {
	pv.mu.RLock()
	defer pv.mu.RUnlock()
	return len(pv.versions) == 0
}

// RefreshablePropertyVersion is a PropertyVersion that reloads its versions
// from a Configuration[string] on Refresh(), splitting the comma-separated
// value stored under key. It mirrors Java's RefreshablePropertyVersion.
type RefreshablePropertyVersion struct {
	*PropertyVersion

	key           string
	configuration Configuration[string]
}

// NewRefreshablePropertyVersion creates a RefreshablePropertyVersion for
// key, sourcing values from configuration, and performs an initial Refresh.
func NewRefreshablePropertyVersion(key string, configuration Configuration[string]) *RefreshablePropertyVersion {
	rpv := &RefreshablePropertyVersion{PropertyVersion: NewPropertyVersion(nil), key: key, configuration: configuration}
	rpv.Refresh()
	return rpv
}

// Refresh reloads the comma-separated versions from the underlying
// Configuration.
func (rpv *RefreshablePropertyVersion) Refresh() {
	rpv.setVersions(GetValues[string](rpv.configuration, rpv.key))
}

var _ Refreshable = (*RefreshablePropertyVersion)(nil)

// RegisterRefreshablePropertyVersion returns the RefreshablePropertyVersion
// registered under key in ctx's PropertyRegistry, creating (and
// registering) one if absent. It mirrors Java's
// RefreshablePropertyVersion.register.
func RegisterRefreshablePropertyVersion(ctx *Context, key string) *RefreshablePropertyVersion {
	v := ctx.PropertyRegistry().RegisterIfAbsent(key, func() any {
		return NewRefreshablePropertyVersion(key, ctx.Configuration())
	})
	return v.(*RefreshablePropertyVersion)
}
