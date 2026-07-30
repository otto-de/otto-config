// Package endpoint implements the REST configuration endpoint
// ("/configs", "/configs/{key}", "/{app}/configs", "/{app}/configs/{key}")
// as a net/http Handler, backed by per-app ottoconfig.Provider[string]
// instances built with secrets always excluded. It is a faithful port of
// Java's ProviderEndpoint plus its Spring/Helidon
// integration.*.endpoint.ConfigurationEndpoint subclasses.
package endpoint

import (
	"encoding/json"
	"net/http"
	"strings"
	"sync"

	ottoconfig "github.com/otto-de/otto-config/go"
)

// EnabledConfigKey is the configuration key that gates whether the REST
// endpoint should be mounted at all (checked by callers, not by Handler
// itself, mirroring Java's @ConditionalOnProperty /
// getOptionalValue("otto.config.endpoint.configs.enabled", ...)).
const EnabledConfigKey = "otto.config.endpoint.configs.enabled"

// appsConfigKey lists additional app names (besides ctx.AppName()) that this
// endpoint should also expose, mirroring Java's
// "otto.config.endpoint.configs.apps".
const appsConfigKey = "otto.config.endpoint.configs.apps"

// Enabled reports whether the REST configuration endpoint should be
// mounted, per EnabledConfigKey (default false).
func Enabled(ctx *ottoconfig.Context) bool {
	return ottoconfig.GetValueAsBool(ctx.Configuration(), EnabledConfigKey, false)
}

// Handler serves the configuration REST endpoint for ctx's app, plus any
// additional apps listed under appsConfigKey. Every exposed Provider is
// built with secrets excluded, regardless of ctx's own ExcludeSecrets
// setting, so the endpoint never leaks secret-bearing properties.
type Handler struct {
	ctx *ottoconfig.Context

	mu        sync.RWMutex
	providers map[string]*ottoconfig.Provider[string]
}

// NewHandler creates a Handler and eagerly builds providers for ctx's app
// name plus every app listed in appsConfigKey, mirroring Java's
// ProviderEndpoint.registerProviders (called from constructors).
func NewHandler(ctx *ottoconfig.Context) (*Handler, error) {
	h := &Handler{ctx: ctx, providers: make(map[string]*ottoconfig.Provider[string])}
	if err := h.registerProviders(); err != nil {
		return nil, err
	}
	return h, nil
}

func (h *Handler) registerProviders() error {
	for _, app := range h.appNames() {
		if _, err := h.createProvider(app); err != nil {
			return err
		}
	}
	return nil
}

// appNames returns the deduplicated set of app names to expose: ctx's own
// app name plus every (trimmed, non-empty) entry in appsConfigKey.
func (h *Handler) appNames() []string {
	set := map[string]struct{}{h.ctx.AppName(): {}}
	for _, app := range ottoconfig.GetValues[string](h.ctx.Configuration(), appsConfigKey) {
		set[app] = struct{}{}
	}
	names := make([]string, 0, len(set))
	for app := range set {
		names = append(names, app)
	}
	return names
}

// createProvider builds a fresh Context for app (same profile and bootstrap
// configuration as h.ctx, but ExcludeSecrets forced to true) and a
// ConfigurationProvider over it, caches it, and also registers it with
// h.ctx's ProviderRegistry so that h.ctx.Refresh()/PollAndRefresh() also
// refreshes these secondary per-app providers -- mirroring Java's dual
// registration in ProviderEndpoint.registerProviders/Provider's
// constructor.
func (h *Handler) createProvider(app string) (*ottoconfig.Provider[string], error) {
	appCtx, err := ottoconfig.NewContext(app,
		ottoconfig.WithProfile(h.ctx.Profile()),
		ottoconfig.WithConfiguration(h.ctx.Configuration()),
		ottoconfig.WithExcludeSecrets(true),
	)
	if err != nil {
		return nil, err
	}

	provider := ottoconfig.NewConfigurationProvider(appCtx)

	h.mu.Lock()
	h.providers[app] = provider
	h.mu.Unlock()

	h.ctx.ProviderRegistry().Register(provider)

	return provider, nil
}

func (h *Handler) provider(app string) (*ottoconfig.Provider[string], bool) {
	h.mu.RLock()
	defer h.mu.RUnlock()
	p, ok := h.providers[app]
	return p, ok
}

// Mux builds a *http.ServeMux serving:
//
//	GET /configs             -- all values for ctx's own app, as JSON
//	GET /configs/{key}       -- a single value for ctx's own app, as plain text
//	GET /{app}/configs       -- all values for {app}, as JSON
//	GET /{app}/configs/{key} -- a single value for {app}, as plain text
//
// Requests for an app not registered via appNames() get a 404.
//
// These four patterns are registered by hand (rather than via
// mux.HandleFunc per-pattern) because Go's net/http.ServeMux (1.22+)
// rejects "GET /{app}/configs" and "GET /configs/{key}" as ambiguous: no
// path pattern is more specific than the other for every path they could
// both match (e.g. "/configs/configs"). Java's Spring MVC/JAX-RS routers
// resolve the identical four-route scheme (see ProviderEndpoint's
// concrete subclasses) by preferring literal path segments over path
// variables, which the routing below replicates: a literal leading
// "configs" segment always wins over the "{app}" route.
func (h *Handler) Mux() *http.ServeMux {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /", h.route)
	return mux
}

// route dispatches a request path to the appropriate own-app or per-app
// configs handler, per the four-route scheme documented on Mux.
func (h *Handler) route(w http.ResponseWriter, r *http.Request) {
	segments := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
	switch {
	case len(segments) == 1 && segments[0] == "configs":
		h.writeValues(w, h.ctx.AppName())
	case len(segments) == 2 && segments[0] == "configs":
		h.writeValue(w, h.ctx.AppName(), segments[1])
	case len(segments) == 2 && segments[1] == "configs":
		h.writeValues(w, segments[0])
	case len(segments) == 3 && segments[1] == "configs":
		h.writeValue(w, segments[0], segments[2])
	default:
		http.NotFound(w, r)
	}
}

// ServeHTTP makes Handler itself usable as an http.Handler.
func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	h.Mux().ServeHTTP(w, r)
}

func (h *Handler) writeValues(w http.ResponseWriter, app string) {
	provider, ok := h.provider(app)
	if !ok {
		http.NotFound(w, nil)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(provider.AsMap())
}

func (h *Handler) writeValue(w http.ResponseWriter, app, key string) {
	provider, ok := h.provider(app)
	if !ok {
		http.NotFound(w, nil)
		return
	}
	value, ok := provider.GetValue(key)
	if !ok {
		http.NotFound(w, nil)
		return
	}
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	_, _ = w.Write([]byte(value))
}
