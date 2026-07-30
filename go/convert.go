package ottoconfig

import "strconv"

// GetValueByType looks up key in c and converts the stored string value to
// T2 (string, int, int64, float64, float32, or bool). It returns the zero
// value and false if the key is missing, or T2 is unsupported, or the value
// cannot be parsed as T2. It mirrors Java's Configuration.getValueByType,
// but reports failure via the bool result instead of throwing.
func GetValueByType[T2 any](c Configuration[string], key string) (T2, bool) {
	var zero T2
	s, ok := c.GetValue(key)
	if !ok {
		return zero, false
	}
	return convertValue[T2](s)
}

func convertValue[T2 any](s string) (T2, bool) {
	var zero T2
	var result any

	switch any(zero).(type) {
	case string:
		result = s
	case int:
		n, err := strconv.Atoi(s)
		if err != nil {
			return zero, false
		}
		result = n
	case int64:
		n, err := strconv.ParseInt(s, 10, 64)
		if err != nil {
			return zero, false
		}
		result = n
	case float64:
		n, err := strconv.ParseFloat(s, 64)
		if err != nil {
			return zero, false
		}
		result = n
	case float32:
		n, err := strconv.ParseFloat(s, 32)
		if err != nil {
			return zero, false
		}
		result = float32(n)
	case bool:
		b, err := strconv.ParseBool(s)
		if err != nil {
			return zero, false
		}
		result = b
	default:
		return zero, false
	}

	return result.(T2), true
}
