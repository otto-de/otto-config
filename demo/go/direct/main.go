// Command direct shows how to use otto-config as a plain library inside a
// long-running application: no HTTP server, no gin router, no REST
// endpoint. Configuration values are read directly in Go code via the
// Provider (and via a bound struct), and refreshed in the background by the
// scheduler package. This is the pattern to follow when otto-config just
// needs to feed values into your own application logic.
//
// Configuration points at the demo/local docker-compose stack (moto + vault
// + appconfigdata-stub), the same as demo/go/gin. Start it first (from
// the repository root); vault-init needs a few seconds to write .env, so
// wait for it rather than sourcing immediately:
//
//	cd demo/local && docker compose up -d
//	until [ -f .env ]; do sleep 1; done
//	source ./.env && cd ../go
//
// Then run:
//
//	go run ./direct
package main

import (
	"context"
	"fmt"
	"log"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	ottoconfig "github.com/otto-de/otto-config/go"
	"github.com/otto-de/otto-config/go/bind"
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

// AppConfig is a normal Go struct, populated straight from otto-config via
// struct tags -- the equivalent of Java's @PropertyValue-annotated fields,
// but usable without any DI framework.
type AppConfig struct {
	MyKey1  string `config:"myKey1"`
	MyKey2  string `config:"myKey2"`
	Logging bool   `config:"logging.enabled,default=false"`
}

func main() {
	slog.Info("Starting direct otto-config usage example...")

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
		"otto.config.aws.change.notifications.enabled":       "false",
	}

	configuration := ottoconfig.NewCacheWithProperties(seed)

	ctx, err := ottoconfig.NewContext("otto-config",
		ottoconfig.WithProfile(envOr("OTTO_CONFIG_PROFILE", "default")),
		ottoconfig.WithConfiguration(configuration),
	)
	if err != nil {
		log.Fatalf("failed to create context: %v", err)
	}

	// A Provider is the whole public API for reading aggregated values --
	// no server needed.
	provider := ottoconfig.NewConfigurationProvider(ctx)

	// bind.Register populates a plain struct once, and keeps it up to date
	// on every subsequent refresh.
	var appConfig AppConfig
	if _, err := bind.Register(ctx, "app-config", &appConfig); err != nil {
		log.Fatalf("failed to bind AppConfig: %v", err)
	}

	// Keep values fresh in the background (periodic full refresh + fast
	// event-driven poll), matching what the Spring/Helidon integrations do
	// automatically for you.
	if s := scheduler.StartDefault(ctx); s != nil {
		defer s.Stop()
	}

	printValues := func() {
		fmt.Println("--- current configuration ---")
		fmt.Println("myKey1 (via Provider) =", provider.GetValueOr("myKey1", "<missing>"))
		fmt.Println("myKey1 (via bound struct) =", appConfig.MyKey1)
		fmt.Println("myKey2 =", appConfig.MyKey2)
		fmt.Println("logging.enabled =", appConfig.Logging)
		fmt.Println("some_secret =", provider.GetValueOr("some_secret", "<missing>"))
		fmt.Println("some_ssm_value =", provider.GetValueOr("some_ssm_value", "<missing>"))
	}

	printValues()

	// Simulate a long-running application that periodically reads the
	// (possibly refreshed) configuration, until interrupted.
	stop, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-stop.Done():
			slog.Info("Shutting down direct otto-config usage example...")
			return
		case <-ticker.C:
			printValues()
		}
	}
}
