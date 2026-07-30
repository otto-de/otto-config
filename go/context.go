package ottoconfig

import "sync"

// ChangeListener polls an external notification channel (e.g. SQS) and
// triggers refreshes on matching sources. It mirrors Java's
// SourceChangeEventListener.
type ChangeListener interface {
	PollAndRefresh()
}

// ChangeListenerFactoryFunc attempts to build a ChangeListener for ctx,
// returning ok=false if its preconditions (e.g. required configuration
// keys) aren't met.
type ChangeListenerFactoryFunc func(ctx *Context) (listener ChangeListener, ok bool, err error)

var (
	changeListenerFactoriesMu sync.RWMutex
	changeListenerFactories   []ChangeListenerFactoryFunc
)

// RegisterChangeListenerFactory registers a factory that Context evaluates
// while it is being constructed. The event package (SQS-based
// change-notification support) registers itself this way, keeping the root
// package free of a direct dependency on it.
func RegisterChangeListenerFactory(factory ChangeListenerFactoryFunc) {
	changeListenerFactoriesMu.Lock()
	defer changeListenerFactoriesMu.Unlock()
	changeListenerFactories = append(changeListenerFactories, factory)
}

func discoverChangeListeners(ctx *Context) ([]ChangeListener, error) {
	changeListenerFactoriesMu.RLock()
	defer changeListenerFactoriesMu.RUnlock()

	var listeners []ChangeListener
	for _, factory := range changeListenerFactories {
		listener, ok, err := factory(ctx)
		if err != nil {
			return nil, err
		}
		if ok {
			listeners = append(listeners, listener)
		}
	}
	return listeners, nil
}

// Context holds everything needed to load, aggregate, and refresh
// configuration for one application: the bootstrap Configuration (used to
// read otto.config.* keys), and the Source/Provider/Property/Client
// registries. It mirrors Java's Context.
type Context struct {
	appName        string
	profile        string
	excludeSecrets bool

	mu            sync.RWMutex
	configuration Configuration[string]

	clientRegistry   *ClientRegistry
	sourceRegistry   *SourceRegistry
	providerRegistry *ProviderRegistry
	propertyRegistry *PropertyRegistry
	changeListeners  []ChangeListener
}

// ContextOption customizes Context construction. See With* functions.
type ContextOption func(*contextOptions)

type contextOptions struct {
	profile          string
	excludeSecrets   bool
	configuration    Configuration[string]
	clientRegistry   *ClientRegistry
	propertyRegistry *PropertyRegistry
}

// WithProfile sets the active profile (e.g. "local", "production"). Certain
// sources fall back to a local file when the profile is "local", "test", or
// "integration-test".
func WithProfile(profile string) ContextOption {
	return func(o *contextOptions) { o.profile = profile }
}

// WithExcludeSecrets configures whether properties loaded from
// secret-bearing sources (Secrets Manager, Vault) are excluded from
// aggregated providers/endpoints.
func WithExcludeSecrets(exclude bool) ContextOption {
	return func(o *contextOptions) { o.excludeSecrets = exclude }
}

// WithConfiguration sets the bootstrap Configuration used to read
// otto.config.* keys (e.g. otto.config.sources.enabled). Defaults to an
// empty Cache[string] if not provided.
func WithConfiguration(configuration Configuration[string]) ContextOption {
	return func(o *contextOptions) { o.configuration = configuration }
}

// WithClientRegistry sets a pre-built ClientRegistry. Defaults to
// NewDefaultClientRegistry() if not provided.
func WithClientRegistry(clientRegistry *ClientRegistry) ContextOption {
	return func(o *contextOptions) { o.clientRegistry = clientRegistry }
}

// WithPropertyRegistry sets a pre-built PropertyRegistry. Defaults to
// NewPropertyRegistry() if not provided.
func WithPropertyRegistry(propertyRegistry *PropertyRegistry) ContextOption {
	return func(o *contextOptions) { o.propertyRegistry = propertyRegistry }
}

// NewContext builds a Context for appName, discovering sources registered
// via RegisterSourceFactory according to the "otto.config.sources.enabled"
// key of the (possibly default) bootstrap configuration.
func NewContext(appName string, opts ...ContextOption) (*Context, error) {
	options := &contextOptions{}
	for _, opt := range opts {
		opt(options)
	}

	ctx := &Context{
		appName:          appName,
		profile:          options.profile,
		excludeSecrets:   options.excludeSecrets,
		configuration:    options.configuration,
		clientRegistry:   options.clientRegistry,
		providerRegistry: NewProviderRegistry(),
		propertyRegistry: options.propertyRegistry,
	}
	if ctx.configuration == nil {
		ctx.configuration = NewCache[string]()
	}
	if ctx.clientRegistry == nil {
		ctx.clientRegistry = NewDefaultClientRegistry()
	}
	if ctx.propertyRegistry == nil {
		ctx.propertyRegistry = NewPropertyRegistry()
	}

	sources, err := DiscoverSources(ctx)
	if err != nil {
		return nil, err
	}
	ctx.sourceRegistry = NewSourceRegistry(sources...)

	listeners, err := discoverChangeListeners(ctx)
	if err != nil {
		return nil, err
	}
	ctx.changeListeners = listeners

	return ctx, nil
}

// From creates a Context for appName with the "default" profile and an
// empty bootstrap configuration. It mirrors Java's Context.from(appName).
func From(appName string) (*Context, error) {
	return NewContext(appName, WithProfile("default"))
}

// FromWithConfiguration creates a Context for appName with the given
// profile and bootstrap configuration. It mirrors Java's
// Context.from(appName, profile, configuration).
func FromWithConfiguration(appName, profile string, configuration Configuration[string]) (*Context, error) {
	return NewContext(appName, WithProfile(profile), WithConfiguration(configuration))
}

// AppName returns the application name.
func (c *Context) AppName() string { return c.appName }

// Profile returns the active profile.
func (c *Context) Profile() string { return c.profile }

// ExcludeSecrets reports whether secret-bearing sources are excluded from
// aggregated providers/endpoints.
func (c *Context) ExcludeSecrets() bool { return c.excludeSecrets }

// Configuration returns the bootstrap Configuration.
func (c *Context) Configuration() Configuration[string] {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.configuration
}

// SetConfiguration replaces the bootstrap Configuration.
func (c *Context) SetConfiguration(configuration Configuration[string]) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.configuration = configuration
}

// ClientRegistry returns the shared client registry.
func (c *Context) ClientRegistry() *ClientRegistry { return c.clientRegistry }

// SourceRegistry returns the source registry.
func (c *Context) SourceRegistry() *SourceRegistry { return c.sourceRegistry }

// ProviderRegistry returns the provider registry.
func (c *Context) ProviderRegistry() *ProviderRegistry { return c.providerRegistry }

// PropertyRegistry returns the property registry.
func (c *Context) PropertyRegistry() *PropertyRegistry { return c.propertyRegistry }

// IsLocalProfile reports whether profile is one of "local", "test", or
// "integration-test" (or empty), the profiles for which sources fall back
// to reading a local file instead of calling their external backend.
func (c *Context) IsLocalProfile() bool {
	switch c.profile {
	case "", "local", "test", "integration-test":
		return true
	default:
		return false
	}
}

// Refresh performs a full refresh: all registered providers and properties
// are reloaded from scratch.
func (c *Context) Refresh() {
	c.providerRegistry.Refresh()
	c.propertyRegistry.Refresh()
}

// PollAndRefresh polls any registered ChangeListeners (e.g. SQS) and, if any
// are configured, performs a lightweight in-place refresh of providers and
// properties. It is a no-op if no change listeners were discovered.
func (c *Context) PollAndRefresh() {
	if len(c.changeListeners) == 0 {
		return
	}
	for _, listener := range c.changeListeners {
		listener.PollAndRefresh()
	}
	c.providerRegistry.RefreshInPlace()
	c.propertyRegistry.RefreshInPlace()
}
