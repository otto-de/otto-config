package de.otto.config.core.property;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PropertyNameNormalizer {
    
    /**
     * Generate property name variants to support relaxed binding.
     * Used by all frameworks (Spring, Helidon, plain Java).
     */
    public static String[] generateVariants(String name) {
        if (!name.contains("-") && !name.contains("_") && name.equals(name.toLowerCase())) {
            return new String[0];
        }
        
        String[] segments = name.split("\\.");
        StringBuilder camelCase = new StringBuilder();
        StringBuilder kebabCase = new StringBuilder();
        StringBuilder underscore = new StringBuilder();
        StringBuilder upperUnderscore = new StringBuilder();
        StringBuilder lowerUnderscore = new StringBuilder();
        
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                camelCase.append(".");
                kebabCase.append(".");
                underscore.append(".");
                upperUnderscore.append(".");
                lowerUnderscore.append(".");
            }
            
            String segment = segments[i];
            camelCase.append(toCamelCase(segment));
            kebabCase.append(toKebabCase(segment));
            underscore.append(segment.replace('-', '_'));
            lowerUnderscore.append(segment.replace('-', '_').toLowerCase());
            upperUnderscore.append(segment.replace('-', '_').toUpperCase());
        }
        
        return new String[] {
            camelCase.toString(),
            kebabCase.toString(),
            underscore.toString(),
            lowerUnderscore.toString(),
            upperUnderscore.toString(),
            name.replace('-', '_'),
            name.replace('_', '-'),
        };
    }
    
    static String toCamelCase(String segment) {
        if (!segment.contains("-") && !segment.contains("_")) {
            return segment;
        }
        
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;
        boolean isFirst = true;
        
        for (char c : segment.toCharArray()) {
            if (c == '-' || c == '_') {
                capitalizeNext = true;
                isFirst = false;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
                isFirst = false;
            } else {
                if (isFirst) {
                    result.append(Character.toLowerCase(c));
                    isFirst = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
        }
        
        return result.toString();
    }
    
    static String toKebabCase(String segment) {
        if (segment.isEmpty()) {
            return segment;
        }
        
        String withHyphens = segment.replace('_', '-');
        
        boolean isAllUppercase = true;
        for (char c : withHyphens.toCharArray()) {
            if (Character.isLetter(c) && !Character.isUpperCase(c)) {
                isAllUppercase = false;
                break;
            }
        }
        
        if (isAllUppercase) {
            return withHyphens.toLowerCase();
        }
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < withHyphens.length(); i++) {
            char c = withHyphens.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && withHyphens.charAt(i - 1) != '-') {
                    result.append('-');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
}
