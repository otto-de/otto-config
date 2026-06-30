package de.otto.config.fixture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClientBuilder;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationRequest;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationResponse;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionRequest;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionResponse;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.secretsmanager.model.BatchGetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.BatchGetSecretValueResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.SsmClientBuilder;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.paginators.GetParametersByPathIterable;

public class MockAwsClients {
   public static void withMockedAwsClients(Runnable testCode) {
        AppConfigDataClient mockAppConfigDataClient = mockAppConfigDataClient();
        AppConfigDataClientBuilder mockAppConfigDataClientBuilder = Mockito.mock(AppConfigDataClientBuilder.class);

        SecretsManagerClient mockSecretsManagerClient = mockSecretsManagerClient();
        SecretsManagerClientBuilder mockSecretsManagerClientBuilder = Mockito.mock(SecretsManagerClientBuilder.class);

        SsmClient mockSsmClient = mockSsmClient();
        SsmClientBuilder mockSsmClientBuilder = Mockito.mock(SsmClientBuilder.class);

        S3Client mockS3Client = mockS3Client();
        S3ClientBuilder mockS3ClientBuilder = Mockito.mock(S3ClientBuilder.class);

        try (
            MockedStatic<AppConfigDataClient> appConfigStatic = Mockito.mockStatic(AppConfigDataClient.class);
            MockedStatic<SecretsManagerClient> secretsManagerStatic = Mockito.mockStatic(SecretsManagerClient.class);
            MockedStatic<SsmClient> ssmStatic = Mockito.mockStatic(SsmClient.class);
            MockedStatic<S3Client> s3Static = Mockito.mockStatic(S3Client.class)
        ) {
            appConfigStatic.when(AppConfigDataClient::builder).thenReturn(mockAppConfigDataClientBuilder);
            Mockito.when(mockAppConfigDataClientBuilder.build()).thenReturn(mockAppConfigDataClient);

            secretsManagerStatic.when(SecretsManagerClient::builder).thenReturn(mockSecretsManagerClientBuilder);
            Mockito.when(mockSecretsManagerClientBuilder.build()).thenReturn(mockSecretsManagerClient);

            ssmStatic.when(SsmClient::builder).thenReturn(mockSsmClientBuilder);
            Mockito.when(mockSsmClientBuilder.build()).thenReturn(mockSsmClient);

            s3Static.when(S3Client::builder).thenReturn(mockS3ClientBuilder);
            Mockito.when(mockS3ClientBuilder.build()).thenReturn(mockS3Client);

            testCode.run();
        }
    }

    public static AppConfigDataClient mockAppConfigDataClient() {
        AppConfigDataClient client = mock(AppConfigDataClient.class);
        StartConfigurationSessionResponse startConfigurationSessionResponse = StartConfigurationSessionResponse.builder()
                                                                                                               .initialConfigurationToken("")
                                                                                                               .build();
        when(client.startConfigurationSession(any(StartConfigurationSessionRequest.class))).thenReturn(startConfigurationSessionResponse);

        GetLatestConfigurationResponse getLatestConfigurationResponse = GetLatestConfigurationResponse.builder()
                                                                                                      .configuration(SdkBytes.fromUtf8String(""))
                                                                                                      .build();
        when(client.getLatestConfiguration(any(GetLatestConfigurationRequest.class))).thenReturn(getLatestConfigurationResponse);
        return client;
    }

    public static SecretsManagerClient mockSecretsManagerClient() {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        BatchGetSecretValueResponse response = BatchGetSecretValueResponse.builder()
                                                                          .secretValues(Collections.emptyList())
                                                                          .build();
        when(client.batchGetSecretValue(any(BatchGetSecretValueRequest.class))).thenReturn(response);
        return client;
    }

    public static SsmClient mockSsmClient() {
        SsmClient client = mock(SsmClient.class);
        GetParametersByPathRequest request = GetParametersByPathRequest.builder().path("/").build();
        GetParametersByPathResponse response = GetParametersByPathResponse.builder().parameters(Collections.emptySet()).build();
        GetParametersByPathIterable paginator = new GetParametersByPathIterable(client, request);

        when(client.getParametersByPathPaginator(any(GetParametersByPathRequest.class))).thenReturn(paginator);
        when(client.getParametersByPath(any(GetParametersByPathRequest.class))).thenReturn(response);
        return client;
    }

    public static S3Client mockS3Client() {
        S3Client client = mock(S3Client.class);
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket("").prefix("").build();
        ListObjectsV2Response response = ListObjectsV2Response.builder().build();
        when(client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
                .thenReturn(new ListObjectsV2Iterable(client, request));
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);
        return client;
    }
}
