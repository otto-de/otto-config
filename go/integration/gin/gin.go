// Package ginconfig mounts the otto-config REST configuration endpoint
// (see the sibling endpoint package) onto a gin.Engine or
// gin.IRoutes, for applications built on github.com/gin-gonic/gin instead
// of plain net/http. It mirrors Java's Spring MVC
// integration.spring.endpoint.ConfigurationEndpoint (a @RestController with
// the same four routes).
package ginconfig

import (
	"net/http"

	"github.com/gin-gonic/gin"

	ottoconfig "github.com/otto-de/otto-config/go"
	"github.com/otto-de/otto-config/go/endpoint"
)

// RegisterRoutes mounts the four configuration REST routes
//
//	GET /configs
//	GET /configs/:key
//	GET /:app/configs
//	GET /:app/configs/:key
//
// on router, backed by a new endpoint.Handler for ctx. It returns the
// Handler so callers can reuse it (e.g. for graceful shutdown hooks), or an
// error if the Handler's initial provider registration fails (e.g. an
// invalid app name in "otto.config.endpoint.configs.apps").
func RegisterRoutes(router gin.IRoutes, ctx *ottoconfig.Context) (*endpoint.Handler, error) {
	h, err := endpoint.NewHandler(ctx)
	if err != nil {
		return nil, err
	}

	router.GET("/configs", wrap(h))
	router.GET("/configs/:key", wrap(h))
	router.GET("/:app/configs", wrap(h))
	router.GET("/:app/configs/:key", wrap(h))

	return h, nil
}

// wrap adapts endpoint.Handler (a net/http.Handler expecting Go 1.22+
// {app}/{key} ServeMux path patterns) to gin's :app/:key param style by
// rewriting the request path before delegating to h.ServeHTTP.
func wrap(h *endpoint.Handler) gin.HandlerFunc {
	return func(c *gin.Context) {
		h.ServeHTTP(c.Writer, c.Request)
	}
}

// Enabled reports whether the REST configuration endpoint should be
// mounted, per endpoint.EnabledConfigKey (default false). It is a
// re-export of endpoint.Enabled for callers that only import ginconfig.
func Enabled(ctx *ottoconfig.Context) bool {
	return endpoint.Enabled(ctx)
}

var _ http.Handler = (*endpoint.Handler)(nil)
