package ottoconfig

import (
	"strings"
	"unicode"
)

// GenerateVariants generates property name variants (camelCase, kebab-case,
// snake_case, SCREAMING_SNAKE_CASE, lower_snake_case, plus simple
// underscore/hyphen swaps) to support relaxed binding across the naming
// conventions used by different frameworks (Spring, Helidon, plain Go, etc).
// It mirrors Java's PropertyNameNormalizer.generateVariants.
//
// Returns nil if name is already all-lowercase with no hyphens/underscores,
// since no variants would help in that case.
func GenerateVariants(name string) []string {
	if !strings.ContainsAny(name, "-_") && name == strings.ToLower(name) {
		return nil
	}

	segments := strings.Split(name, ".")
	var camelCase, kebabCase, underscore, upperUnderscore, lowerUnderscore strings.Builder

	for i, segment := range segments {
		if i > 0 {
			camelCase.WriteByte('.')
			kebabCase.WriteByte('.')
			underscore.WriteByte('.')
			upperUnderscore.WriteByte('.')
			lowerUnderscore.WriteByte('.')
		}

		camelCase.WriteString(toCamelCase(segment))
		kebabCase.WriteString(toKebabCase(segment))

		withUnderscore := strings.ReplaceAll(segment, "-", "_")
		underscore.WriteString(withUnderscore)
		lowerUnderscore.WriteString(strings.ToLower(withUnderscore))
		upperUnderscore.WriteString(strings.ToUpper(withUnderscore))
	}

	return []string{
		camelCase.String(),
		kebabCase.String(),
		underscore.String(),
		lowerUnderscore.String(),
		upperUnderscore.String(),
		strings.ReplaceAll(name, "-", "_"),
		strings.ReplaceAll(name, "_", "-"),
	}
}

func toCamelCase(segment string) string {
	if !strings.ContainsAny(segment, "-_") {
		return segment
	}

	var result strings.Builder
	capitalizeNext := false
	for _, c := range segment {
		switch {
		case c == '-' || c == '_':
			capitalizeNext = true
		case capitalizeNext:
			result.WriteRune(unicode.ToUpper(c))
			capitalizeNext = false
		default:
			result.WriteRune(unicode.ToLower(c))
		}
	}
	return result.String()
}

func toKebabCase(segment string) string {
	if segment == "" {
		return segment
	}

	withHyphens := strings.ReplaceAll(segment, "_", "-")

	isAllUppercase := true
	for _, c := range withHyphens {
		if unicode.IsLetter(c) && !unicode.IsUpper(c) {
			isAllUppercase = false
			break
		}
	}
	if isAllUppercase {
		return strings.ToLower(withHyphens)
	}

	runes := []rune(withHyphens)
	var result strings.Builder
	for i, c := range runes {
		if unicode.IsUpper(c) {
			if i > 0 && runes[i-1] != '-' {
				result.WriteByte('-')
			}
			result.WriteRune(unicode.ToLower(c))
		} else {
			result.WriteRune(c)
		}
	}
	return result.String()
}
