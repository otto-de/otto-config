package de.otto.config.client.hashicorp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.client.hashicorp.VaultException;
import de.otto.config.client.hashicorp.VaultResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.time.Instant;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class VaultAwsAuthenticatorTest {

    private ObjectMapper objectMapper;
    private VaultAwsAuthenticator authenticator;
    private static final String TEST_URL = "https://vault.example.com";
    private static final String TEST_ROLE = "test-role";
    private static final String TEST_ROLE_ARN = "arn:aws:iam::123456789012:role/test-role";
    private static final String TEST_REGION = "us-east-1";
    private static final String TEST_HEADER_VALUE = "vault-server-id";

    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();
        authenticator = spy(VaultAwsAuthenticator.builder()
                .url(TEST_URL)
                .headerValue(TEST_HEADER_VALUE)
                .role(TEST_ROLE)
                .roleArn(TEST_ROLE_ARN)
                .region(TEST_REGION)
                .objectMapper(objectMapper)
                .build());
    }

    @Test
    public void shouldThrowVaultExceptionWhenAssumeRoleFails() {
        assertThrows(VaultException.class, () -> {
            authenticator.generateToken();
        });
    }

    @Test
    public void shouldGenerateValidTokenWhenAuthenticationSucceeds() throws Exception {
        // given
        StsClient mockStsClient = mock(StsClient.class);
        Credentials mockCredentials = Credentials.builder()
                .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                .secretAccessKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
                .sessionToken("sessionToken")
                .expiration(Instant.now().plusSeconds(3600))
                .build();
        
        AssumeRoleResponse mockAssumeRoleResponse = AssumeRoleResponse.builder()
                .credentials(mockCredentials)
                .build();
        
        when(mockStsClient.assumeRole(any(AssumeRoleRequest.class)))
                .thenReturn(mockAssumeRoleResponse);

        VaultResponse.Auth mockAuth = new VaultResponse.Auth("test-token", 3600);
        VaultResponse mockVaultResponse = new VaultResponse(null, mockAuth);
        
        doReturn(mockVaultResponse).when(authenticator)
                .post(anyString(), anyString(), anyMap());

        try (MockedStatic<StsClient> stsClientStatic = mockStatic(StsClient.class)) {
            StsClientBuilder mockBuilder = mock(StsClientBuilder.class);
            when(mockBuilder.region(any())).thenReturn(mockBuilder);
            when(mockBuilder.credentialsProvider(any(DefaultCredentialsProvider.class)))
                    .thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockStsClient);
            stsClientStatic.when(StsClient::builder).thenReturn(mockBuilder);

            // when
            authenticator.generateToken();

            // then
            assertThat(authenticator.getToken(), equalTo("test-token"));
            assertThat(authenticator.getLeaseDuration(), equalTo(3600L));
            assertThat(authenticator.getTokenExpiry(), is(notNullValue()));
        }
    }

    @Test
    public void shouldSetTokenExpiryBasedOnLeaseDuration() throws Exception {
        // given
        StsClient mockStsClient = mock(StsClient.class);
        Credentials mockCredentials = Credentials.builder()
                .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                .secretAccessKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
                .sessionToken("sessionToken")
                .expiration(Instant.now().plusSeconds(3600))
                .build();
        
        AssumeRoleResponse mockAssumeRoleResponse = AssumeRoleResponse.builder()
                .credentials(mockCredentials)
                .build();
        
        when(mockStsClient.assumeRole(any(AssumeRoleRequest.class)))
                .thenReturn(mockAssumeRoleResponse);

        VaultResponse.Auth mockAuth = new VaultResponse.Auth("test-token", 7200);
        VaultResponse mockVaultResponse = new VaultResponse(null, mockAuth);
        
        doReturn(mockVaultResponse).when(authenticator)
                .post(anyString(), anyString(), anyMap());

        try (MockedStatic<StsClient> stsClientStatic = mockStatic(StsClient.class)) {
            StsClientBuilder mockBuilder = mock(StsClientBuilder.class);
            when(mockBuilder.region(any())).thenReturn(mockBuilder);
            when(mockBuilder.credentialsProvider(any(DefaultCredentialsProvider.class)))
                    .thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockStsClient);
            stsClientStatic.when(StsClient::builder).thenReturn(mockBuilder);

            Instant beforeGeneration = Instant.now();

            // when
            authenticator.generateToken();

            // then
            Instant tokenExpiry = authenticator.getTokenExpiry();
            assertThat(tokenExpiry, is(greaterThan(beforeGeneration.plusSeconds(7190))));
            assertThat(tokenExpiry, is(lessThan(beforeGeneration.plusSeconds(7210))));
        }
    }

    @Test
    public void shouldUseAssumeRoleWhenRoleArnIsProvided() throws Exception {
        // given
        StsClient mockStsClient = mock(StsClient.class);
        Credentials mockCredentials = Credentials.builder()
                        .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                        .secretAccessKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
                        .sessionToken("sessionToken")
                        .expiration(Instant.now().plusSeconds(3600))
                        .build();

        AssumeRoleResponse mockAssumeRoleResponse = AssumeRoleResponse.builder()
                        .credentials(mockCredentials)
                        .build();

        when(mockStsClient.assumeRole(any(AssumeRoleRequest.class)))
                        .thenReturn(mockAssumeRoleResponse);

        VaultResponse.Auth mockAuth = new VaultResponse.Auth("token-with-rolearn", 1000);
        VaultResponse mockVaultResponse = new VaultResponse(null, mockAuth);

        VaultAwsAuthenticator authenticatorWithRoleArn = spy(VaultAwsAuthenticator.builder()
                        .url(TEST_URL)
                        .headerValue(TEST_HEADER_VALUE)
                        .role(TEST_ROLE)
                        .roleArn(TEST_ROLE_ARN)
                        .region(TEST_REGION)
                        .objectMapper(objectMapper)
                        .build());

        doReturn(mockVaultResponse).when(authenticatorWithRoleArn)
                        .post(anyString(), anyString(), anyMap());

        try (MockedStatic<StsClient> stsClientStatic = mockStatic(StsClient.class)) {
            StsClientBuilder mockBuilder = mock(StsClientBuilder.class);
            when(mockBuilder.region(any())).thenReturn(mockBuilder);
            when(mockBuilder.credentialsProvider(any(DefaultCredentialsProvider.class)))
                                .thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockStsClient);
            stsClientStatic.when(StsClient::builder).thenReturn(mockBuilder);

            // when
            authenticatorWithRoleArn.generateToken();

            // then
            assertThat(authenticatorWithRoleArn.getToken(), equalTo("token-with-rolearn"));
        }
    }

    @Test
    public void shouldUseCurrentSessionCredentialsWhenRoleArnIsNotProvided() throws Exception {
        // given
        VaultAwsAuthenticator authenticatorNoRoleArn = spy(VaultAwsAuthenticator.builder()
                        .url(TEST_URL)
                        .headerValue(TEST_HEADER_VALUE)
                        .role(TEST_ROLE)
                        .roleArn(null)
                        .region(TEST_REGION)
                        .objectMapper(objectMapper)
                        .build());

        AwsSessionCredentials mockSessionCreds = AwsSessionCredentials.create(
                        "AKIAIOSFODNN7EXAMPLE",
                        "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                        "sessionToken"
        );

        VaultResponse.Auth mockAuth = new VaultResponse.Auth("token-without-rolearn", 2000);
        VaultResponse mockVaultResponse = new VaultResponse(null, mockAuth);

        // Mock DefaultCredentialsProvider to return our mock session credentials
        try (MockedStatic<DefaultCredentialsProvider> providerStatic = mockStatic(DefaultCredentialsProvider.class)) {
            DefaultCredentialsProvider mockProvider = mock(DefaultCredentialsProvider.class);
            when(mockProvider.resolveCredentials()).thenReturn(mockSessionCreds);
            providerStatic.when(DefaultCredentialsProvider::create).thenReturn(mockProvider);

            // Mock post to return our mock Vault response
            doReturn(mockVaultResponse).when(authenticatorNoRoleArn)
                                .post(anyString(), anyString(), anyMap());

            // when
            authenticatorNoRoleArn.generateToken();

            // then
            assertThat(authenticatorNoRoleArn.getToken(), equalTo("token-without-rolearn"));
            // No assumeRole should be called, so nothing to verify there
        }
    }

    @Test
    public void shouldWrapExceptionWhenTokenGenerationFails() throws Exception {
        // given
        doThrow(new RuntimeException("Network error"))
                .when(authenticator).post(anyString(), anyString(), anyMap());

        // when/then
        VaultException exception = assertThrows(VaultException.class, () -> {
            authenticator.generateToken();
        });
        
        assertThat(exception.getMessage(), containsString("Failed to generate Vault token"));
    }
}
