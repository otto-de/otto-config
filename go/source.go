package ottoconfig

import (
	"fmt"
	"log/slog"
	"regexp"
	"strings"
	"sync"
)

// RawConfig is implemented by the data loaded by individual Sources (e.g.
// domain.Properties, domain.Toggles). It mirrors Java's wildcard
// Configuration<?> as used throughout the Source system.
type RawConfig interface {
	// Values returns all key/value pairs, with values boxed as any.
	Values() map[string]any
	// IsEmpty reports whether there is no data at all.
	IsEmpty() bool
	// Kind identifies the shape of this RawConfig (e.g. "properties",
	// "toggles"). ConfigurationProvider uses it to select which sources to
	// aggregate, playing the role of Java's
	// Class<? extends Configuration<?>> comparison in
	// SourceRegistry.filterByType.
	Kind() string
}

// ChangeEvent is a parsed notification describing an external configuration
// change (e.g. delivered via SQS/EventBridge). It mirrors Java's
// SourceChangeEvent.
type ChangeEvent interface {
	EventSource() string
	DetailType() string
}

// Source loads a RawConfig from a backend (AWS AppConfig, Secrets Manager,
// SSM, S3, Vault, or a local file) and caches the last-known-good value. It
// mirrors Java's abstract Source<T> class.
type Source interface {
	Refreshable

	// Load fetches fresh data from the backend. Implementations should
	// return an empty RawConfig (not an error) when polled and nothing has
	// changed.
	Load() (RawConfig, error)
	// Empty returns the zero-value RawConfig for this source's kind.
	Empty() RawConfig
	// Kind identifies this source's RawConfig shape (see RawConfig.Kind).
	Kind() string
	// HasSecrets reports whether the properties loaded by this source
	// contain secret material that should be excluded from user-facing
	// aggregates/endpoints when the caller opts in to secret exclusion.
	HasSecrets() bool
	// OnChanged is invoked for every ChangeEvent when event-driven refresh
	// is enabled. Implementations should return true (and typically call
	// Refresh) if the event pertains to this source.
	OnChanged(event ChangeEvent) bool
	// PullRefreshEnabled reports whether this source supports being
	// refreshed by polling (as opposed to purely event-driven refresh).
	PullRefreshEnabled() bool
	// GetOrLoad returns the cached value, reloading first if forceReload is
	// true or there is no usable cached value yet.
	GetOrLoad(forceReload bool) RawConfig
}

// CachedSource provides the caching behaviour shared by all Source
// implementations (the non-abstract parts of Java's Source<T> base class).
// Concrete sources embed *CachedSource and supply LoadFunc/EmptyFunc, then
// add their own Kind/HasSecrets/OnChanged/PullRefreshEnabled as needed
// (embedding's default HasSecrets/OnChanged/PullRefreshEnabled can be
// shadowed by defining a method with the same name on the outer type).
type CachedSource struct {
	mu        sync.Mutex
	cache     RawConfig
	LoadFunc  func() (RawConfig, error)
	EmptyFunc func() RawConfig
	Logger    *slog.Logger
}

// NewCachedSource creates a CachedSource that calls load to fetch fresh data
// and empty to produce the zero value when nothing has ever loaded
// successfully.
func NewCachedSource(load func() (RawConfig, error), empty func() RawConfig) *CachedSource {
	return &CachedSource{LoadFunc: load, EmptyFunc: empty}
}

// Load fetches fresh data via LoadFunc without touching the cache.
func (s *CachedSource) Load() (RawConfig, error) {
	return s.LoadFunc()
}

// Empty returns the zero-value RawConfig via EmptyFunc.
func (s *CachedSource) Empty() RawConfig {
	return s.EmptyFunc()
}

// HasSecrets defaults to false; override by shadowing on the embedding type.
func (s *CachedSource) HasSecrets() bool { return false }

// OnChanged defaults to false (no event-driven refresh support); override by
// shadowing on the embedding type.
func (s *CachedSource) OnChanged(event ChangeEvent) bool { return false }

// PullRefreshEnabled defaults to true; override by shadowing on the
// embedding type.
func (s *CachedSource) PullRefreshEnabled() bool { return true }

// GetOrLoad returns the cached value, reloading first if forceReload is true
// or there is no usable cached value yet. Load errors are logged and
// swallowed, falling back to the last-known-good cache (or the empty
// value) -- mirroring Java's Source.getOrLoad.
func (s *CachedSource) GetOrLoad(forceReload bool) RawConfig {
	s.mu.Lock()
	defer s.mu.Unlock()

	if forceReload || s.cache == nil || s.cache.IsEmpty() {
		value, err := s.LoadFunc()
		if err != nil {
			s.logger().Error("error loading configuration from source", "error", err)
		} else if value != nil && !value.IsEmpty() {
			s.cache = value
		}
	}
	if s.cache != nil {
		return s.cache
	}
	return s.EmptyFunc()
}

// Refresh forces a reload.
func (s *CachedSource) Refresh() {
	s.GetOrLoad(true)
}

func (s *CachedSource) logger() *slog.Logger {
	if s.Logger != nil {
		return s.Logger
	}
	return slog.Default()
}

var (
	keyReplacer        = strings.NewReplacer("/", ".", "_", ".")
	leadingDotsPattern = regexp.MustCompile(`^\.+`)
)

func normalizeAggregateKey(key string) string {
	normalized := keyReplacer.Replace(key)
	return leadingDotsPattern.ReplaceAllString(normalized, "")
}

// Aggregate merges the properties loaded by sources into a single map,
// converting each raw value via transform. Earlier sources win on key
// collisions (first-write-wins). It mirrors Java's
// SourceAggregator.aggregate.
func Aggregate[T any](sources []Source, transform func(any) T, normalizeKeys, forceReload, excludeSecrets bool) map[string]T {
	result := make(map[string]T)

	for _, source := range sources {
		raw := source.GetOrLoad(forceReload)
		for key, value := range raw.Values() {
			if _, exists := result[key]; exists {
				continue
			}
			if excludeSecrets && source.HasSecrets() {
				continue
			}

			transformed := transform(value)
			result[key] = transformed

			if normalizeKeys {
				normalizedKey := normalizeAggregateKey(key)
				if normalizedKey != key {
					if _, exists := result[normalizedKey]; !exists {
						result[normalizedKey] = transformed
					}
				}
			}
		}
	}

	return result
}

// SourceRegistry holds the sources discovered/registered for a Context and
// supports filtering by RawConfig kind. It mirrors Java's SourceRegistry.
type SourceRegistry struct {
	*ListRegistry[Source]
}

// NewSourceRegistry creates a SourceRegistry pre-populated with sources.
func NewSourceRegistry(sources ...Source) *SourceRegistry {
	return &SourceRegistry{ListRegistry: NewListRegistry(sources...)}
}

// FilterByKind returns the registered sources whose Kind() is in kinds.
func (r *SourceRegistry) FilterByKind(kinds []string) []Source {
	set := make(map[string]struct{}, len(kinds))
	for _, k := range kinds {
		set[k] = struct{}{}
	}

	var out []Source
	for _, s := range r.Values() {
		if _, ok := set[s.Kind()]; ok {
			out = append(out, s)
		}
	}
	return out
}

// SourceFactoryFunc constructs a Source for the given Context. It plays the
// role of a Java @SourceCreator-annotated method.
type SourceFactoryFunc func(ctx *Context) (Source, error)

var (
	sourceFactoriesMu sync.RWMutex
	sourceFactories   = map[string][]SourceFactoryFunc{}
)

// RegisterSourceFactory registers a factory function for the given source id
// (e.g. "aws.appconfig.properties"). Source packages call this from an
// init() function; consumers enable individual sources at runtime via the
// "otto.config.sources.enabled" configuration key. This replaces Java's
// ServiceLoader + @SourceCreator reflection-based discovery with an
// idiomatic Go registry, similar to how database/sql drivers register
// themselves.
func RegisterSourceFactory(id string, factory SourceFactoryFunc) {
	sourceFactoriesMu.Lock()
	defer sourceFactoriesMu.Unlock()
	sourceFactories[id] = append(sourceFactories[id], factory)
}

// DiscoverSources builds the list of Sources enabled via the
// "otto.config.sources.enabled" (comma-separated) configuration key on ctx,
// in declaration order, using factories registered via
// RegisterSourceFactory.
func DiscoverSources(ctx *Context) ([]Source, error) {
	enabled := GetValues[string](ctx.Configuration(), "otto.config.sources.enabled")

	sourceFactoriesMu.RLock()
	defer sourceFactoriesMu.RUnlock()

	var sources []Source
	for _, id := range enabled {
		for _, factory := range sourceFactories[id] {
			src, err := factory(ctx)
			if err != nil {
				return nil, fmt.Errorf("creating source %q: %w", id, err)
			}
			sources = append(sources, src)
		}
	}
	return sources, nil
}
