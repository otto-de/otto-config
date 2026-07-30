// Package bind provides struct-tag based binding of configuration values
// into plain Go structs, as a lightweight alternative to Java's
// @PropertyValue annotation (used there for Spring/Helidon dependency
// injection). Fields are tagged with `config:"key"`, optionally with a
// default value: `config:"key,default=value"`.
//
// Bind performs a one-shot binding. Register additionally wires the target
// into a Context's PropertyRegistry so it is automatically re-bound on
// Context.Refresh()/PollAndRefresh(), mirroring Java's
// RefreshableProperty/PropertyValue(refreshable=true) semantics.
package bind

import (
	"fmt"
	"reflect"
	"strconv"
	"strings"
	"sync"

	ottoconfig "github.com/otto-de/otto-config/go"
)

const tagName = "config"

// providerMu and providers memoize one aggregated Provider[string] per
// Context, shared across every Register call for that Context, so multiple
// bound targets don't each redundantly re-aggregate sources and so a
// single Refresh() pass keeps them all consistent.
var (
	providerMu sync.Mutex
	providers  = map[*ottoconfig.Context]*ottoconfig.Provider[string]{}
)

// sharedProvider returns the memoized aggregated Provider[string] for ctx,
// creating it (via ottoconfig.NewConfigurationProvider, the same
// "properties"+"toggles" aggregation used by the library's main
// Provider[string] entry point) on first use.
func sharedProvider(ctx *ottoconfig.Context) *ottoconfig.Provider[string] {
	providerMu.Lock()
	defer providerMu.Unlock()
	if p, ok := providers[ctx]; ok {
		return p
	}
	p := ottoconfig.NewConfigurationProvider(ctx)
	providers[ctx] = p
	return p
}

// Bind populates the exported, `config`-tagged fields of the struct pointed
// to by target from cfg. Nested (non-pointer) struct fields are recursed
// into, whether or not they themselves carry a `config` tag, so related
// settings can be grouped into sub-structs.
//
// A field with no matching configuration value keeps its existing value,
// unless the tag specifies "default=...". Values that fail to convert to
// the field's type are treated as not found (per this library's convention
// of degrading to zero/default values rather than erroring; see
// GetValueByType).
func Bind(cfg ottoconfig.Configuration[string], target any) error {
	v := reflect.ValueOf(target)
	if v.Kind() != reflect.Pointer || v.IsNil() {
		return fmt.Errorf("bind: target must be a non-nil pointer to a struct, got %T", target)
	}
	elem := v.Elem()
	if elem.Kind() != reflect.Struct {
		return fmt.Errorf("bind: target must point to a struct, got pointer to %s", elem.Kind())
	}
	return bindStruct(cfg, elem)
}

// MustBind is like Bind but panics on error, for terse use at program
// startup.
func MustBind(cfg ottoconfig.Configuration[string], target any) {
	if err := Bind(cfg, target); err != nil {
		panic(err)
	}
}

func bindStruct(cfg ottoconfig.Configuration[string], v reflect.Value) error {
	t := v.Type()
	for i := 0; i < t.NumField(); i++ {
		field := t.Field(i)
		fv := v.Field(i)
		if field.PkgPath != "" || !fv.CanSet() {
			continue // unexported
		}

		tag, hasTag := field.Tag.Lookup(tagName)
		if !hasTag {
			if fv.Kind() == reflect.Struct {
				if err := bindStruct(cfg, fv); err != nil {
					return err
				}
			}
			continue
		}
		if tag == "-" {
			continue
		}

		key, def, hasDefault := parseTag(tag)

		raw, found := cfg.GetValue(key)
		if !found {
			if !hasDefault {
				continue
			}
			raw = def
		}

		if err := setField(fv, raw); err != nil {
			return fmt.Errorf("bind: field %s (key %q): %w", field.Name, key, err)
		}
	}
	return nil
}

// parseTag splits a `config:"key"` or `config:"key,default=value"` tag into
// its key and optional default.
func parseTag(tag string) (key, def string, hasDefault bool) {
	parts := strings.SplitN(tag, ",", 2)
	key = parts[0]
	if len(parts) == 2 && strings.HasPrefix(parts[1], "default=") {
		return key, strings.TrimPrefix(parts[1], "default="), true
	}
	return key, "", false
}

func setField(fv reflect.Value, raw string) error {
	switch fv.Kind() {
	case reflect.String:
		fv.SetString(raw)
	case reflect.Bool:
		b, err := strconv.ParseBool(raw)
		if err != nil {
			return nil // degrade to no-op, consistent with GetValueAsBool's default-on-failure convention
		}
		fv.SetBool(b)
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		n, err := strconv.ParseInt(raw, 10, 64)
		if err != nil {
			return nil
		}
		fv.SetInt(n)
	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		n, err := strconv.ParseUint(raw, 10, 64)
		if err != nil {
			return nil
		}
		fv.SetUint(n)
	case reflect.Float32, reflect.Float64:
		f, err := strconv.ParseFloat(raw, 64)
		if err != nil {
			return nil
		}
		fv.SetFloat(f)
	case reflect.Slice:
		if fv.Type().Elem().Kind() != reflect.String {
			return fmt.Errorf("unsupported slice element type %s", fv.Type().Elem())
		}
		var values []string
		for _, s := range strings.Split(raw, ",") {
			if s = strings.TrimSpace(s); s != "" {
				values = append(values, s)
			}
		}
		fv.Set(reflect.ValueOf(values))
	default:
		return fmt.Errorf("unsupported field type %s", fv.Kind())
	}
	return nil
}

// Binder re-applies Bind to its target on every Refresh/RefreshInPlace,
// implementing ottoconfig.Refreshable.
type Binder struct {
	cfg    ottoconfig.Configuration[string]
	target any
}

var _ ottoconfig.Refreshable = (*Binder)(nil)

// Refresh re-binds target from the underlying Configuration, logging (via
// the Bind error) but not panicking if a field's tag/type is invalid --
// mirroring how other Refreshable components in this library swallow
// per-refresh errors rather than propagating them up through
// Context.Refresh().
func (b *Binder) Refresh() {
	_ = Bind(b.cfg, b.target)
}

// Register performs an initial Bind of target from ctx's aggregated
// configuration (the same "properties"+"toggles" source data returned by
// ottoconfig.NewConfigurationProvider, shared across all Register calls for
// ctx), then registers a Binder under name in ctx's PropertyRegistry so
// target is automatically re-bound on every subsequent Context.Refresh()/
// PollAndRefresh() call. It mirrors Java's
// RefreshableProperty.register/@PropertyValue(refreshable=true), adapted
// for Go's lack of a framework layer (e.g. Spring's Environment) to proxy
// Context's bootstrap Configuration through to live source data.
//
// name must be unique per bound target within ctx's PropertyRegistry (e.g.
// a dotted path or the target type's name); registering the same name
// twice returns the existing Binder without re-binding target again.
func Register(ctx *ottoconfig.Context, name string, target any) (*Binder, error) {
	provider := sharedProvider(ctx)
	if err := Bind(provider, target); err != nil {
		return nil, err
	}
	v := ctx.PropertyRegistry().RegisterIfAbsent(name, func() any {
		return &Binder{cfg: provider, target: target}
	})
	return v.(*Binder), nil
}
