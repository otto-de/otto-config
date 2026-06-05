package de.otto.config.client.hashicorp.auth;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.base.Charsets;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;

import de.otto.config.client.hashicorp.VaultException;
import de.otto.config.client.hashicorp.VaultHeaders;
import de.otto.config.client.hashicorp.VaultResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

@Slf4j
@Getter
public class VaultAwsAuthenticator extends VaultAuthenticator {
    private static final String STS_REQUEST_BODY = "Action=GetCallerIdentity&Version=2011-06-15";
    private static final String LOGIN_PATH = "/v1/auth/aws/login";

    private final @NonNull Region region;
    private final @NonNull String role;
    private final String roleArn;
    private final @NonNull String headerValue;
    private final @NonNull ObjectMapper objectMapper;
    private final String stsUrl;
    
    @Builder
    public VaultAwsAuthenticator(String url, String headerValue, String role, String roleArn, String region, ObjectMapper objectMapper) {
        super(url, VaultResponse.class, objectMapper);
        this.region = region != null ? Region.of(region) : new DefaultAwsRegionProviderChain().getRegion();
        this.role = role;
        this.roleArn = roleArn;
        this.headerValue = headerValue;
        this.objectMapper = objectMapper;
        this.stsUrl = "https://sts." + this.region.id() + ".amazonaws.com/";
    }

    @Override
    public void generateToken() throws VaultException {
         try {
            Map<String, Object> awsAuthPayload = this.roleArn != null &&
                                                 !this.roleArn.isEmpty()
                                                    ? createAwsAuthPayload(loadCredentialsFromRole())
                                                    : createAwsAuthPayload(loadCredentialsFromSession());
            
            String requestBody = this.objectMapper.writeValueAsString(awsAuthPayload);
            VaultResponse response = this.post(this.url + LOGIN_PATH,
                                               requestBody,
                                               Map.of(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString()));
            
            updateToken(response);
            
        } catch (Exception e) {
            throw new VaultException("Failed to generate Vault token: " + e.getMessage(), e);
        }
    }

    private Credentials loadCredentialsFromRole() throws VaultException {
        try (StsClient stsClient = StsClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {

            AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
                    .roleArn(roleArn)
                    .roleSessionName("vault-login-session")
                    .build();

            AssumeRoleResponse response = stsClient.assumeRole(assumeRoleRequest);
            return response.credentials();
            
        } catch (Exception e) {
            throw new VaultException("Failed to load credentials from role: " + e.getMessage(), e);
        }
    }

    private AwsSessionCredentials loadCredentialsFromSession() throws VaultException {
        try {
            AwsCredentials creds = DefaultCredentialsProvider.create().resolveCredentials();
            if (creds instanceof AwsSessionCredentials) {
                return (AwsSessionCredentials) creds;
            } else {
                // If not a session credentials (i.e., no session token), create a session
                // credentials with null token
                return AwsSessionCredentials.create(
                        creds.accessKeyId(),
                        creds.secretAccessKey(),
                        null);
            }
        } catch (Exception e) {
            throw new VaultException("Failed to load credentials from session: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> createAwsAuthPayload(Credentials credentials) {
        return createAwsAuthPayload(
            AwsSessionCredentials.create(
                credentials.accessKeyId(),
                credentials.secretAccessKey(),
                credentials.sessionToken()
            )
        );
    }

    @SuppressWarnings({ "deprecation", "null" })
    private Map<String, Object> createAwsAuthPayload(AwsSessionCredentials awsCredentials) {
         // Build the HTTP request to sign
        SdkHttpFullRequest.Builder requestBuilder = SdkHttpFullRequest.builder()
                .uri(URI.create(this.stsUrl))
                .method(SdkHttpMethod.POST)
                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.FORM_DATA.withCharset(Charsets.UTF_8).toString())
                .putHeader(VaultHeaders.VAULT_AWS_IAM_SERVER_ID, headerValue)
                .contentStreamProvider(() ->
                    new ByteArrayInputStream(STS_REQUEST_BODY.getBytes(Charsets.UTF_8)));

        // Sign the request
        Aws4Signer signer = Aws4Signer.create();
        Aws4SignerParams signerParams = Aws4SignerParams.builder()
                .awsCredentials(awsCredentials)
                .signingName("sts")
                .signingRegion(region)
                .build();

        SdkHttpFullRequest signedRequest = signer.sign(requestBuilder.build(), signerParams);

        // Extract headers from signed request
        List<String> iamRequestHeaders = signedRequest.headers().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(value -> entry.getKey() + ":" + value))
                .collect(Collectors.toList());

        // Build the Vault authentication payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("role", this.role);
        payload.put("iam_http_request_method", "POST");
        payload.put("iam_request_url", Base64.getEncoder().encodeToString(stsUrl.getBytes(Charsets.UTF_8)));
        payload.put("iam_request_body", Base64.getEncoder().encodeToString(STS_REQUEST_BODY.getBytes(Charsets.UTF_8)));
        payload.put("iam_request_headers", iamRequestHeaders);

        return payload;
    }
}
