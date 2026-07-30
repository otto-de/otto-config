package ottoconfig

import "testing"

func TestConvertValue_String(t *testing.T) {
	c := NewCacheWithProperties(map[string]string{"k": "hello"})
	v, ok := GetValueByType[string](c, "k")
	if !ok || v != "hello" {
		t.Fatalf("expected (\"hello\", true), got (%q, %v)", v, ok)
	}
}

func TestConvertValue_Int(t *testing.T) {
	c := NewCacheWithProperties(map[string]string{"k": "42"})
	v, ok := GetValueByType[int](c, "k")
	if !ok || v != 42 {
		t.Fatalf("expected (42, true), got (%d, %v)", v, ok)
	}
}

func TestConvertValue_IntInvalid(t *testing.T) {
	c := NewCacheWithProperties(map[string]string{"k": "not-a-number"})
	_, ok := GetValueByType[int](c, "k")
	if ok {
		t.Fatal("expected ok=false for unparsable int")
	}
}

func TestConvertValue_Bool(t *testing.T) {
	c := NewCacheWithProperties(map[string]string{"k": "true"})
	v, ok := GetValueByType[bool](c, "k")
	if !ok || !v {
		t.Fatalf("expected (true, true), got (%v, %v)", v, ok)
	}
}

func TestConvertValue_Float64(t *testing.T) {
	c := NewCacheWithProperties(map[string]string{"k": "3.14"})
	v, ok := GetValueByType[float64](c, "k")
	if !ok || v != 3.14 {
		t.Fatalf("expected (3.14, true), got (%v, %v)", v, ok)
	}
}

func TestConvertValue_MissingKey(t *testing.T) {
	c := NewCacheWithProperties(map[string]string{})
	_, ok := GetValueByType[string](c, "missing")
	if ok {
		t.Fatal("expected ok=false for missing key")
	}
}

func TestConvertValue_UnsupportedType(t *testing.T) {
	c := NewCacheWithProperties(map[string]string{"k": "1"})
	_, ok := GetValueByType[[]string](c, "k")
	if ok {
		t.Fatal("expected ok=false for unsupported target type")
	}
}
