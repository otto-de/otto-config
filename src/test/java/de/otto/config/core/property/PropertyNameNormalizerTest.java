package de.otto.config.core.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PropertyNameNormalizerTest {

    @Nested
    class GenerateVariantsTests {

        @Test
        void shouldGenerateVariantsForKebabCase() {
            // Given
            String property = "aws.s3.bucket-name";
            
            // When
            String[] variants = PropertyNameNormalizer.generateVariants(property);
            
            // Then
            assertEquals(7, variants.length);
            assertTrue(containsVariant(variants, "aws.s3.bucketName"), "Should contain camelCase variant");
            assertTrue(containsVariant(variants, "aws.s3.bucket-name"), "Should contain kebab-case variant");
            assertTrue(containsVariant(variants, "aws.s3.bucket_name"), "Should contain underscore variant");
        }

        @Test
        void shouldGenerateVariantsForCamelCase() {
            // Given
            String property = "aws.s3.bucketName";
            
            // When
            String[] variants = PropertyNameNormalizer.generateVariants(property);
            
            // Then
            assertEquals(7, variants.length);
            assertTrue(containsVariant(variants, "aws.s3.bucket-name"), "Should contain kebab-case variant");
            assertTrue(containsVariant(variants, "aws.s3.bucketName"), "Should contain camelCase variant");
        }

        @Test
        void shouldGenerateVariantsForUnderscoreCase() {
            // Given
            String property = "database.user_name";
            
            // When
            String[] variants = PropertyNameNormalizer.generateVariants(property);
            
            // Then
            assertEquals(7, variants.length);
            assertTrue(containsVariant(variants, "database.userName"), "Should contain camelCase variant");
            assertTrue(containsVariant(variants, "database.user-name"), "Should contain kebab-case variant");
            assertTrue(containsVariant(variants, "database.user_name"), "Should contain underscore variant");
        }

        @Test
        void shouldGenerateVariantsForUppercaseUnderscore() {
            // Given
            String property = "app.MAX_CONNECTIONS";
            
            // When
            String[] variants = PropertyNameNormalizer.generateVariants(property);
            
            // Then
            assertEquals(7, variants.length);
            assertTrue(containsVariant(variants, "app.maxConnections"), "Should contain camelCase variant");
            assertTrue(containsVariant(variants, "app.max-connections"), "Should contain kebab-case variant");
            assertTrue(containsVariant(variants, "app.max_connections"), "Should contain lowercase underscore variant");
            assertTrue(containsVariant(variants, "app.MAX_CONNECTIONS"), "Should contain uppercase underscore variant");
        }

        @Test
        void shouldReturnEmptyArrayForSimpleLowercaseProperty() {
            // Given
            String property = "simple.property.name";
            
            // When
            String[] variants = PropertyNameNormalizer.generateVariants(property);
            
            // Then
            assertEquals(0, variants.length, "Should not generate variants for simple lowercase properties");
        }

        @Test
        void shouldHandleDeeplyNestedProperties() {
            // Given
            String property = "spring.security.oauth2.resource-server.jwt.jwk-set-uri";
            
            // When
            String[] variants = PropertyNameNormalizer.generateVariants(property);
            
            // Then
            assertEquals(7, variants.length);
            assertTrue(containsVariant(variants, "spring.security.oauth2.resourceServer.jwt.jwkSetUri"), 
                      "Should contain fully camelCase variant");
            assertTrue(containsVariant(variants, "spring.security.oauth2.resource-server.jwt.jwk-set-uri"), 
                      "Should contain kebab-case variant");
        }

        @Test
        void shouldHandleSingleSegmentProperty() {
            // Given
            String property = "bucket-name";
            
            // When
            String[] variants = PropertyNameNormalizer.generateVariants(property);
            
            // Then
            assertEquals(7, variants.length);
            assertTrue(containsVariant(variants, "bucketName"), "Should contain camelCase variant");
            assertTrue(containsVariant(variants, "bucket-name"), "Should contain kebab-case variant");
            assertTrue(containsVariant(variants, "bucket_name"), "Should contain underscore variant");
        }

        @Test
        void shouldHandleMixedSeparators() {
            // Given
            String property = "app.some-property_name";
            
            // When
            String[] variants = PropertyNameNormalizer.generateVariants(property);
            
            // Then
            assertEquals(7, variants.length);
            assertTrue(containsVariant(variants, "app.somePropertyName"), "Should contain camelCase variant");
        }

        private boolean containsVariant(String[] variants, String expected) {
            for (String variant : variants) {
                if (expected.equals(variant)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Nested
    class ToCamelCaseTests {

        @Test
        void shouldConvertKebabCaseToCamelCase() {
            assertEquals("bucketName", PropertyNameNormalizer.toCamelCase("bucket-name"));
            assertEquals("maxConnections", PropertyNameNormalizer.toCamelCase("max-connections"));
            assertEquals("jwkSetUri", PropertyNameNormalizer.toCamelCase("jwk-set-uri"));
        }

        @Test
        void shouldConvertUnderscoreToCamelCase() {
            assertEquals("bucketName", PropertyNameNormalizer.toCamelCase("bucket_name"));
            assertEquals("maxConnections", PropertyNameNormalizer.toCamelCase("max_connections"));
            assertEquals("userName", PropertyNameNormalizer.toCamelCase("user_name"));
        }

        @Test
        void shouldConvertUppercaseUnderscoreToCamelCase() {
            assertEquals("maxConnections", PropertyNameNormalizer.toCamelCase("MAX_CONNECTIONS"));
            assertEquals("bucketName", PropertyNameNormalizer.toCamelCase("BUCKET_NAME"));
            assertEquals("userName", PropertyNameNormalizer.toCamelCase("USER_NAME"));
        }

        @Test
        void shouldHandleAlreadyCamelCase() {
            assertEquals("bucketName", PropertyNameNormalizer.toCamelCase("bucketName"));
            assertEquals("maxConnections", PropertyNameNormalizer.toCamelCase("maxConnections"));
        }

        @Test
        void shouldHandleSimpleStrings() {
            assertEquals("simple", PropertyNameNormalizer.toCamelCase("simple"));
            assertEquals("url", PropertyNameNormalizer.toCamelCase("url"));
        }

        @Test
        void shouldHandleMixedSeparators() {
            assertEquals("somePropertyName", PropertyNameNormalizer.toCamelCase("some-property_name"));
            assertEquals("mixedCaseValue", PropertyNameNormalizer.toCamelCase("mixed_case-value"));
        }

        @Test
        void shouldLowercaseFirstCharacter() {
            assertEquals("firstName", PropertyNameNormalizer.toCamelCase("FIRST_NAME"));
            assertEquals("databaseUrl", PropertyNameNormalizer.toCamelCase("DATABASE_URL"));
        }

        @Test
        void shouldHandleConsecutiveSeparators() {
            assertEquals("someValue", PropertyNameNormalizer.toCamelCase("some--value"));
            assertEquals("someValue", PropertyNameNormalizer.toCamelCase("some__value"));
        }
    }

    @Nested
    class ToKebabCaseTests {

        @Test
        void shouldConvertCamelCaseToKebabCase() {
            assertEquals("bucket-name", PropertyNameNormalizer.toKebabCase("bucketName"));
            assertEquals("max-connections", PropertyNameNormalizer.toKebabCase("maxConnections"));
            assertEquals("jwk-set-uri", PropertyNameNormalizer.toKebabCase("jwkSetUri"));
        }

        @Test
        void shouldConvertUnderscoreToKebabCase() {
            assertEquals("bucket-name", PropertyNameNormalizer.toKebabCase("bucket_name"));
            assertEquals("max-connections", PropertyNameNormalizer.toKebabCase("max_connections"));
            assertEquals("user-name", PropertyNameNormalizer.toKebabCase("user_name"));
        }

        @Test
        void shouldConvertUppercaseToKebabCase() {
            assertEquals("bucket-name", PropertyNameNormalizer.toKebabCase("BUCKET_NAME"));
            assertEquals("max-connections", PropertyNameNormalizer.toKebabCase("MAX_CONNECTIONS"));
            assertEquals("user-name", PropertyNameNormalizer.toKebabCase("USER_NAME"));
        }

        @Test
        void shouldHandleAlreadyKebabCase() {
            assertEquals("bucket-name", PropertyNameNormalizer.toKebabCase("bucket-name"));
            assertEquals("max-connections", PropertyNameNormalizer.toKebabCase("max-connections"));
        }

        @Test
        void shouldHandleSimpleStrings() {
            assertEquals("simple", PropertyNameNormalizer.toKebabCase("simple"));
            assertEquals("url", PropertyNameNormalizer.toKebabCase("url"));
        }

        @Test
        void shouldHandleEmptyString() {
            assertEquals("", PropertyNameNormalizer.toKebabCase(""));
        }

        @Test
        void shouldHandleSingleCharacter() {
            assertEquals("a", PropertyNameNormalizer.toKebabCase("a"));
            assertEquals("a", PropertyNameNormalizer.toKebabCase("A"));
        }

        @Test
        void shouldHandleMixedCase() {
            // Note: Consecutive uppercase letters are treated as separate words
            assertEquals("my-database-u-r-l", PropertyNameNormalizer.toKebabCase("myDatabaseURL"));
            assertEquals("http-u-r-l", PropertyNameNormalizer.toKebabCase("httpURL"));
        }

        @Test
        void shouldHandleConsecutiveUppercase() {
            // Note: Consecutive uppercase letters are treated as separate words
            assertEquals("u-r-l-path", PropertyNameNormalizer.toKebabCase("URLPath"));
            assertEquals("h-t-t-p-request", PropertyNameNormalizer.toKebabCase("HTTPRequest"));
        }

        @Test
        void shouldNotAddLeadingHyphen() {
            assertEquals("bucket-name", PropertyNameNormalizer.toKebabCase("BucketName"));
        }
    }

    @Nested
    class IntegrationTests {

        @Test
        void shouldHandleRealWorldSpringSecurityProperty() {
            // Given - a complex real-world property name
            String property = "spring.security.oauth2.resourceServer.jwt.jwkSetUri";
            
            // When
            String[] variants = PropertyNameNormalizer.generateVariants(property);
            
            // Then - should generate kebab-case variant that Spring Boot would request
            assertTrue(containsVariant(variants, "spring.security.oauth2.resource-server.jwt.jwk-set-uri"));
        }

        @Test
        void shouldHandleAwsS3Property() {
            // Given - AWS property stored as camelCase in SSM
            String property = "aws.s3.bucketName";
            
            // When
            String[] variants = PropertyNameNormalizer.generateVariants(property);
            
            // Then - should generate kebab-case variant that @ConfigurationProperties would request
            assertTrue(containsVariant(variants, "aws.s3.bucket-name"));
        }

        @Test
        void shouldHandleDatabaseConnectionProperties() {
            // Given
            String property = "database.connection.maxPoolSize";
            
            // When
            String[] variants = PropertyNameNormalizer.generateVariants(property);
            
            // Then
            // The camelCase is converted to kebab-case with hyphens before each uppercase letter
            assertTrue(containsVariant(variants, "database.connection.max-pool-size"));
            assertTrue(containsVariant(variants, "database.connection.maxpoolsize"));
        }

        @Test
        void shouldBeSymmetricForCamelAndKebabConversion() {
            // Given
            String original = "bucketName";
            
            // When - convert to kebab and back to camel
            String kebab = PropertyNameNormalizer.toKebabCase(original);
            String backToCamel = PropertyNameNormalizer.toCamelCase(kebab);
            
            // Then
            assertEquals("bucket-name", kebab);
            assertEquals("bucketName", backToCamel);
        }

        @Test
        void shouldBeSymmetricForUnderscoreAndCamelConversion() {
            // Given
            String original = "user_name";
            
            // When - convert to camel and back to kebab (which replaces underscore)
            String camel = PropertyNameNormalizer.toCamelCase(original);
            String kebab = PropertyNameNormalizer.toKebabCase(original);
            
            // Then
            assertEquals("userName", camel);
            assertEquals("user-name", kebab);
        }

        private boolean containsVariant(String[] variants, String expected) {
            for (String variant : variants) {
                if (expected.equals(variant)) {
                    return true;
                }
            }
            return false;
        }
    }
}
