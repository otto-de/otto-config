// Command quickstart is the simplest possible otto-config usage example:
// no AWS, no Vault, no HTTP server, no framework -- just a local JSON file
// (embedded into the binary) loaded directly through the library and read
// via a Provider, entirely in plain Go code. Run it with:
//
//	go run ./examples/quickstart
package main

import (
	"embed"
	"fmt"
	"log"

	ottoconfig "github.com/otto-de/otto-config/go"
	"github.com/otto-de/otto-config/go/source/file"
)

//go:embed properties.json
var configFS embed.FS

func main() {
	// 1. Create a Context. With no "otto.config.sources.enabled" set (and no
	// AWS/Vault source packages blank-imported), no external sources are
	// auto-discovered -- we'll register our own file-backed sources below
	// instead.
	ctx, err := ottoconfig.NewContext("quickstart-example")
	if err != nil {
		log.Fatalf("failed to create context: %v", err)
	}

	// 2. Build Sources reading from the embedded properties.json.
	properties := file.NewProperties(configFS, "properties.json", "properties")
	toggles := file.NewToggles(configFS, "properties.json", "toggles")

	// 3. Build a Provider directly from those sources -- this is the whole
	// public API surface you need: no REST endpoint, no gin router.
	provider := ottoconfig.NewConfigurationProvider(ctx, properties, toggles)

	// 4. Read values directly in your application code.
	fmt.Println("myKey     =", provider.GetValueOr("myKey", "<missing>"))
	fmt.Println("otherKey  =", ottoconfig.GetValueAsInt[string](provider, "otherKey", -1))
	fmt.Println("myFeature =", ottoconfig.GetValueAsBool[string](provider, "myFeature", false))

	fmt.Println("\nall values:")
	for k, v := range provider.AsMap() {
		fmt.Printf("  %s = %v\n", k, v)
	}
}
