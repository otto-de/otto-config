// Command plain is a minimal, manual (no-framework) demonstration of
// otto-config in Go, mirroring Java's demo/java Main.java:
//
//  1. Seed a Configuration[string] with the settings a framework would
//     otherwise read from a properties/yaml file.
//  2. Create a Context from that configuration.
//  3. Build a ConfigurationProvider from the Context (sources are
//     auto-discovered from "otto.config.sources.enabled", provided their
//     packages are blank-imported below for factory registration).
//  4. Expose the aggregated values via a plain net/http server at /config.
//
// Port is taken from the SERVER_PORT env var (default 8080). Run it with:
//
//	go run ./examples/plain
package main

import (
	"encoding/json"
	"log"
	"log/slog"
	"net/http"
	"os"

	ottoconfig "github.com/otto-de/otto-config/go"

	_ "github.com/otto-de/otto-config/go/source/appconfig"
	_ "github.com/otto-de/otto-config/go/source/s3toggles"
	_ "github.com/otto-de/otto-config/go/source/secretsmanager"
	_ "github.com/otto-de/otto-config/go/source/ssm"
)

func envOr(key, def string) string {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		return v
	}
	return def
}

func main() {
	slog.Info("Starting plain Go otto-config demo...")

	profile := envOr("OTTO_CONFIG_PROFILE", "default")

	seed := map[string]string{
		"otto.config.sources.enabled":                  "aws.appconfig.properties,aws.appconfig.toggles,aws.s3.toggles,aws.secrets,aws.ssm",
		"otto.config.aws.secrets.arn":                  "otto-config",
		"otto.config.aws.s3.toggles.bucket.name":       "otto-config-feature-toggles",
		"otto.config.aws.s3.toggles.folder.name":       "feature-toggles/",
		"otto.config.aws.ssm.path.prefix":              "/search/develop/otto-config",
		"otto.config.aws.change.notifications.enabled": "false",
	}

	configuration := ottoconfig.NewCacheWithProperties(seed)

	ctx, err := ottoconfig.NewContext("otto-config",
		ottoconfig.WithProfile(profile),
		ottoconfig.WithConfiguration(configuration),
	)
	if err != nil {
		log.Fatalf("failed to create context: %v", err)
	}

	provider := ottoconfig.NewConfigurationProvider(ctx)

	port := envOr("SERVER_PORT", "8080")

	mux := http.NewServeMux()
	mux.HandleFunc("GET /config", func(w http.ResponseWriter, r *http.Request) {
		out := map[string]any{
			"myKey1":          provider.GetValueOr("myKey1", ""),
			"myKey2":          provider.GetValueOr("myKey2", ""),
			"logging.enabled": provider.GetValueOr("logging.enabled", ""),
			"logging_enabled": ottoconfig.GetValueAsBool[string](provider, "logging_enabled", false),
			"s3_toggle1":      ottoconfig.GetValueAsBool[string](provider, "s3_toggle1", false),
			"s3_toggle2":      ottoconfig.GetValueAsBool[string](provider, "s3_toggle2", false),
			"some_secret":     provider.GetValueOr("some_secret", ""),
			"some_ssm_value":  provider.GetValueOr("some_ssm_value", ""),
		}
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		_ = json.NewEncoder(w).Encode(out)
	})

	slog.Info("Plain Go demo listening", "addr", "http://0.0.0.0:"+port+"/config")
	if err := http.ListenAndServe(":"+port, mux); err != nil {
		log.Fatalf("server failed: %v", err)
	}
}
