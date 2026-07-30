package ottoconfig

import "reflect"

// ClientRegistry stores singleton service/client instances keyed by their
// concrete type (e.g. a shared HTTP client or AWS SDK client), so that
// multiple sources can share one instance. It mirrors Java's ClientRegistry,
// using reflect.Type in place of Java's Class<?> token.
type ClientRegistry struct {
	*MapRegistry[reflect.Type, any]
}

// NewClientRegistry creates an empty ClientRegistry.
func NewClientRegistry() *ClientRegistry {
	return &ClientRegistry{MapRegistry: NewMapRegistry[reflect.Type, any]()}
}

// NewDefaultClientRegistry creates a ClientRegistry with no pre-registered
// clients. Unlike Java (which pre-registers a shared Jackson ObjectMapper),
// Go's encoding/json has no shared mutable state to register by default.
func NewDefaultClientRegistry() *ClientRegistry {
	return NewClientRegistry()
}

func clientKey[T any]() reflect.Type {
	return reflect.TypeOf((*T)(nil)).Elem()
}

// RegisterClient stores client, keyed by its static type T.
func RegisterClient[T any](cr *ClientRegistry, client T) {
	cr.Register(clientKey[T](), client)
}

// GetClient returns the client registered for type T, if any.
func GetClient[T any](cr *ClientRegistry) (T, bool) {
	v, ok := cr.Get(clientKey[T]())
	if !ok {
		var zero T
		return zero, false
	}
	client, ok := v.(T)
	return client, ok
}

// GetOrRegisterClient returns the client registered for type T, creating and
// registering one via create if absent.
func GetOrRegisterClient[T any](cr *ClientRegistry, create func() T) T {
	v := cr.RegisterIfAbsent(clientKey[T](), func() any { return create() })
	return v.(T)
}

// GetOrRegisterClientErr is like GetOrRegisterClient, but for clients whose
// construction can fail (e.g. one requiring a network login). If create
// returns an error, nothing is registered and the zero value of T is
// returned alongside the error.
func GetOrRegisterClientErr[T any](cr *ClientRegistry, create func() (T, error)) (T, error) {
	if existing, ok := GetClient[T](cr); ok {
		return existing, nil
	}
	client, err := create()
	if err != nil {
		var zero T
		return zero, err
	}
	RegisterClient(cr, client)
	return client, nil
}
