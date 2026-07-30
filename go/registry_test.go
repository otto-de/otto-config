package ottoconfig

import "testing"

func TestListRegistry(t *testing.T) {
	r := NewListRegistry[string]("a")
	r.Register("b")
	if got := r.Values(); len(got) != 2 || got[0] != "a" || got[1] != "b" {
		t.Fatalf("unexpected values: %v", got)
	}

	// Values() should be a snapshot copy, not aliasing internal state.
	got := r.Values()
	got[0] = "mutated"
	if r.Values()[0] != "a" {
		t.Fatal("Values() must return an independent copy")
	}

	r.Clear()
	if len(r.Values()) != 0 {
		t.Fatal("expected empty registry after Clear")
	}
}

func TestMapRegistry(t *testing.T) {
	r := NewMapRegistry[string, int]()
	r.Register("a", 1)
	if !r.Contains("a") {
		t.Fatal("expected Contains(\"a\") to be true")
	}
	if v, ok := r.Get("a"); !ok || v != 1 {
		t.Fatalf("expected (1, true), got (%d, %v)", v, ok)
	}

	r.Unregister("a")
	if r.Contains("a") {
		t.Fatal("expected Contains(\"a\") to be false after Unregister")
	}
}

func TestMapRegistry_RegisterIfAbsent(t *testing.T) {
	r := NewMapRegistry[string, int]()
	calls := 0
	create := func() int {
		calls++
		return 42
	}

	v1 := r.RegisterIfAbsent("k", create)
	v2 := r.RegisterIfAbsent("k", create)

	if v1 != 42 || v2 != 42 {
		t.Fatalf("expected both calls to return 42, got %d and %d", v1, v2)
	}
	if calls != 1 {
		t.Fatalf("expected create to be called exactly once, got %d", calls)
	}
}
