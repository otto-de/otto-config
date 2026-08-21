// Package ottoconfig is a Go port of the de.otto.config Java library.
//
// It provides a pluggable configuration engine that aggregates values from
// multiple sources (AWS AppConfig, Secrets Manager, SSM Parameter Store, S3
// feature toggles, HashiCorp Vault, and local files) into a single, relaxed
// -binding configuration view, with optional auto-refresh via polling or
// event-driven (SQS/EventBridge) notifications.
package ottoconfig

import (
	"fmt"
	"strconv"
	"strings"
)

// Configuration is a generic, read-only view over a set of configuration
// values of type T. It mirrors the Java `Configuration<T>` interface.
type Configuration[T any] interface {
	// GetValue returns the value for key and whether it was found.
	GetValue(key string) (T, bool)
	// GetValueOr returns the value for key, or def if key was not found.
	GetValueOr(key string, def T) T
	// Properties returns all configured key/value pairs.
	Properties() map[string]T
	// ContainsKey reports whether key is present.
	ContainsKey(key string) bool
	// IsEmpty reports whether there are no properties at all.
	IsEmpty() bool
}

// GetValueAsString returns the value for key converted to its string
// representation (via fmt.Sprint), and whether it was found.
func GetValueAsString[T any](c Configuration[T], key string) (string, bool) {
	v, ok := c.GetValue(key)
	if !ok {
		return "", false
	}
	return fmt.Sprint(v), true
}

// GetValueAsStringOr returns the string representation of the value for key,
// or def if key was not found.
func GetValueAsStringOr[T any](c Configuration[T], key, def string) string {
	if s, ok := GetValueAsString[T](c, key); ok {
		return s
	}
	return def
}

// GetValueAsInt parses the value for key as an int, returning def if the key
// is missing or the value cannot be parsed. Java's getValueAsInt throws a
// NumberFormatException on an unparseable value instead.
func GetValueAsInt[T any](c Configuration[T], key string, def int) int {
	s := GetValueAsStringOr[T](c, key, strconv.Itoa(def))
	n, err := strconv.Atoi(s)
	if err != nil {
		return def
	}
	return n
}

// GetValueAsBool returns true only when the value for key equals "true"
// (case-insensitive), or def if the key is missing. This mirrors Java's
// getValueAsBoolean, which delegates to Boolean.parseBoolean: values such as
// "1", "yes" or "on" are false, not an error and not the default.
func GetValueAsBool[T any](c Configuration[T], key string, def bool) bool {
	s, ok := GetValueAsString[T](c, key)
	if !ok {
		return def
	}
	return strings.EqualFold(s, "true")
}

// GetValues splits the value for key on commas, trims whitespace, drops empty
// segments, and returns the resulting slice (empty slice if key is missing).
func GetValues[T any](c Configuration[T], key string) []string {
	s, ok := GetValueAsString[T](c, key)
	if !ok || s == "" {
		return []string{}
	}
	parts := strings.Split(s, ",")
	result := make([]string, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p != "" {
			result = append(result, p)
		}
	}
	return result
}

// ConfigurationOverride decorates a Configuration[T], letting selected keys
// be overridden without mutating the underlying configuration.
type ConfigurationOverride[T any] struct {
	Configuration[T]
	Overrides map[string]T
}

// WithOverrides returns a Configuration[T] that returns values from overrides
// first, falling back to c for any key not present in overrides.
func WithOverrides[T any](c Configuration[T], overrides map[string]T) Configuration[T] {
	return &ConfigurationOverride[T]{Configuration: c, Overrides: overrides}
}

func (o *ConfigurationOverride[T]) GetValue(key string) (T, bool) {
	if v, ok := o.Overrides[key]; ok {
		return v, true
	}
	return o.Configuration.GetValue(key)
}

func (o *ConfigurationOverride[T]) GetValueOr(key string, def T) T {
	if v, ok := o.GetValue(key); ok {
		return v
	}
	return def
}

func (o *ConfigurationOverride[T]) ContainsKey(key string) bool {
	_, ok := o.GetValue(key)
	return ok
}
