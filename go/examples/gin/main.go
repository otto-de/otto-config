// Command gin is a full framework-style demonstration of otto-config in Go,
// mirroring Java's demo/spring application: all six source kinds enabled,
// the REST configuration endpoint mounted (via the sibling ginconfig
// package), and background refresh scheduling (via the sibling scheduler
// package).
//
// Configuration values point at the demo/local docker-compose stack (moto +
// vault + appconfigdata-stub) by default, matching demo/spring's "moto"
// Spring profile. Vault AppRole credentials come from the VAULT_ROLE_ID /
// VAULT_SECRET_ID env vars (written to demo/local/.env by vault-init.sh).
// Port is taken from the SERVER_PORT env var (default 8080). Run it with:
//
//	cd demo/local && docker compose up -d
//	until [ -f .env ]; do sleep 1; done
//	source ./.env && cd ../../go
//	go run ./examples/gin
package main

import (
	"log"
	"log/slog"
	"os"

	"github.com/gin-gonic/gin"

	ottoconfig "github.com/otto-de/otto-config/go"
	"github.com/otto-de/otto-config/go/bind"
	ginconfig "github.com/otto-de/otto-config/go/integration/gin"
	"github.com/otto-de/otto-config/go/scheduler"

	_ "github.com/otto-de/otto-config/go/source/appconfig"
	_ "github.com/otto-de/otto-config/go/source/s3toggles"
	_ "github.com/otto-de/otto-config/go/source/secretsmanager"
	_ "github.com/otto-de/otto-config/go/source/ssm"
	_ "github.com/otto-de/otto-config/go/source/vault"
)

func envOr(key, def string) string {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		return v
	}
	return def
}

// DemoConfig shows the bind package's struct-tag binding in action -- the
// Go equivalent of Java's @PropertyValue-annotated fields.
type DemoConfig struct {
	MyKey1  string `config:"myKey1"`
	MyKey2  string `config:"myKey2"`
	Logging bool   `config:"logging.enabled,default=false"`
}

func main() {
	slog.Info("Starting Go/gin otto-config demo...")

	profile := envOr("OTTO_CONFIG_PROFILE", "default")

	seed := map[string]string{
		"otto.config.sources.enabled":                        "aws.appconfig.properties,aws.appconfig.toggles,aws.s3.toggles,aws.secrets,aws.ssm,hashicorp.vault",
		"otto.config.aws.secrets.arn":                        "otto-config",
		"otto.config.aws.s3.toggles.bucket.name":             "otto-config-feature-toggles",
		"otto.config.aws.s3.toggles.folder.name":             "feature-toggles/",
		"otto.config.aws.ssm.path.prefix":                    "/search/develop/otto-config",
		"otto.config.hashicorp.vault.url":                    "http://localhost:8200",
		"otto.config.hashicorp.vault.path":                   "cftsearch/data/service/otto-config/develop/auth",
		"otto.config.hashicorp.vault.prev.versions":          "3",
		"otto.config.hashicorp.vault.auth.type":              "approle",
		"otto.config.hashicorp.vault.auth.approle.role.id":   envOr("VAULT_ROLE_ID", ""),
		"otto.config.hashicorp.vault.auth.approle.secret.id": envOr("VAULT_SECRET_ID", ""),
		"otto.config.aws.change.notifications.enabled":       "true",
		"otto.config.aws.change.notifications.queue.url":     "http://localhost:5000/123456789012/otto-config-config-changes",
		"otto.config.endpoint.configs.enabled":               "true",
		"otto.config.endpoint.configs.apps":                  "otto-config",
	}

	configuration := ottoconfig.NewCacheWithProperties(seed)

	ctx, err := ottoconfig.NewContext("otto-config",
		ottoconfig.WithProfile(profile),
		ottoconfig.WithConfiguration(configuration),
	)
	if err != nil {
		log.Fatalf("failed to create context: %v", err)
	}

	var cfg DemoConfig
	if _, err := bind.Register(ctx, "demo-config", &cfg); err != nil {
		log.Fatalf("failed to bind DemoConfig: %v", err)
	}

	if s := scheduler.StartDefault(ctx); s != nil {
		defer s.Stop()
	}

	router := gin.Default()

	if ginconfig.Enabled(ctx) {
		if _, err := ginconfig.RegisterRoutes(router, ctx); err != nil {
			log.Fatalf("failed to register configuration endpoint: %v", err)
		}
	}

	router.GET("/demo-config", func(c *gin.Context) {
		c.JSON(200, cfg)
	})

	port := envOr("SERVER_PORT", "8080")
	slog.Info("Go/gin demo listening", "addr", "http://0.0.0.0:"+port)
	if err := router.Run(":" + port); err != nil {
		log.Fatalf("server failed: %v", err)
	}
}
