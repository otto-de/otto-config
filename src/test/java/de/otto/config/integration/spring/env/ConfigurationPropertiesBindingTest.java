package de.otto.config.integration.spring.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import de.otto.config.integration.spring.fixtures.MockBeans;

/**
 * Integration test that verifies Spring Boot's @ConfigurationProperties works correctly
 * with Otto Config's relaxed binding support. This test simulates the real-world scenario
 * where properties are stored in camelCase (e.g., aws.s3.bucketName) but accessed via
 * @ConfigurationProperties which uses kebab-case by convention.
 */
@SpringBootTest(
    classes = {
        MockBeans.class,
        ConfigurationPropertiesBindingTest.TestConfiguration.class
    },
    properties = {
        "spring.application.name=test-app",
        "spring.profiles.active=test"
    }
)
@TestPropertySource(properties = {
    // Simulate properties loaded from SSM with camelCase naming
    "aws.s3.bucketName=firefly-bucket",
    "aws.s3.regionName=eu-central-1",
    "database.hostName=db.example.com",
    "database.portNumber=5432",
    "database.userName=admin",
    "spring.security.oauth2.resourceServer.jwt.jwkSetUri=https://auth.example.com/.well-known/jwks.json",
    "app.maxConnections=100",
    "app.timeoutSeconds=30"
})
class ConfigurationPropertiesBindingTest {

    @Autowired
    private AwsS3Properties awsS3Properties;

    @Autowired
    private DatabaseProperties databaseProperties;

    @Autowired
    private SpringSecurityProperties springSecurityProperties;

    @Autowired
    private AppProperties appProperties;

    @Test
    void shouldBindCamelCasePropertiesToKebabCaseConfigurationProperties() {
        // Verify AWS S3 properties (camelCase stored, kebab-case field names)
        assertNotNull(awsS3Properties);
        assertEquals("firefly-bucket", awsS3Properties.getBucketName());
        assertEquals("eu-central-1", awsS3Properties.getRegionName());
    }

    @Test
    void shouldBindMultiWordProperties() {
        // Verify database properties with multiple words
        assertNotNull(databaseProperties);
        assertEquals("db.example.com", databaseProperties.getHostName());
        assertEquals(5432, databaseProperties.getPortNumber());
        assertEquals("admin", databaseProperties.getUserName());
    }

    @Test
    void shouldBindDeeplyNestedProperties() {
        // Verify deeply nested properties like spring.security.oauth2.resourceServer.jwt.jwkSetUri
        assertNotNull(springSecurityProperties);
        assertNotNull(springSecurityProperties.getResourceServer());
        assertNotNull(springSecurityProperties.getResourceServer().getJwt());
        assertEquals("https://auth.example.com/.well-known/jwks.json", 
                    springSecurityProperties.getResourceServer().getJwt().getJwkSetUri());
    }

    @Test
    void shouldBindPropertiesWithIntegerTypes() {
        // Verify type conversion works
        assertNotNull(appProperties);
        assertEquals(100, appProperties.getMaxConnections());
        assertEquals(30, appProperties.getTimeoutSeconds());
    }

    // Test configuration classes that mirror the user's real-world scenario

    @Configuration
    @EnableConfigurationProperties({
        AwsS3Properties.class,
        DatabaseProperties.class,
        SpringSecurityProperties.class,
        AppProperties.class
    })
    static class TestConfiguration {
    }

    /**
     * Mirrors the user's AwsS3Properties class.
     * Properties are stored as camelCase (aws.s3.bucketName) but Spring Boot's
     * @ConfigurationProperties uses kebab-case convention (bucket-name field).
     */
    @ConfigurationProperties(prefix = "aws.s3")
    static class AwsS3Properties {
        private String bucketName;  // Spring asks for "aws.s3.bucket-name"
        private String regionName;  // Spring asks for "aws.s3.region-name"

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }

        public String getRegionName() {
            return regionName;
        }

        public void setRegionName(String regionName) {
            this.regionName = regionName;
        }
    }

    @ConfigurationProperties(prefix = "database")
    static class DatabaseProperties {
        private String hostName;    // Spring asks for "database.host-name"
        private Integer portNumber; // Spring asks for "database.port-number"
        private String userName;    // Spring asks for "database.user-name"

        public String getHostName() {
            return hostName;
        }

        public void setHostName(String hostName) {
            this.hostName = hostName;
        }

        public Integer getPortNumber() {
            return portNumber;
        }

        public void setPortNumber(Integer portNumber) {
            this.portNumber = portNumber;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }
    }

    @ConfigurationProperties(prefix = "spring.security.oauth2")
    static class SpringSecurityProperties {
        private ResourceServerProperties resourceServer;

        public ResourceServerProperties getResourceServer() {
            return resourceServer;
        }

        public void setResourceServer(ResourceServerProperties resourceServer) {
            this.resourceServer = resourceServer;
        }

        static class ResourceServerProperties {
            private JwtProperties jwt;

            public JwtProperties getJwt() {
                return jwt;
            }

            public void setJwt(JwtProperties jwt) {
                this.jwt = jwt;
            }

            static class JwtProperties {
                private String jwkSetUri;  // Spring asks for "spring.security.oauth2.resource-server.jwt.jwk-set-uri"

                public String getJwkSetUri() {
                    return jwkSetUri;
                }

                public void setJwkSetUri(String jwkSetUri) {
                    this.jwkSetUri = jwkSetUri;
                }
            }
        }
    }

    @ConfigurationProperties(prefix = "app")
    static class AppProperties {
        private Integer maxConnections;  // Spring asks for "app.max-connections"
        private Integer timeoutSeconds;  // Spring asks for "app.timeout-seconds"

        public Integer getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(Integer maxConnections) {
            this.maxConnections = maxConnections;
        }

        public Integer getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(Integer timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
