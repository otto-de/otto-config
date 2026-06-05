package de.otto.config.integration.spring.fixtures;

import static de.otto.config.fixture.MockAwsClients.mockAppConfigDataClient;
import static de.otto.config.fixture.MockAwsClients.mockSecretsManagerClient;
import static de.otto.config.fixture.MockAwsClients.mockSsmClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ssm.SsmClient;

@Configuration
public class MockBeans {

    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return mockSecretsManagerClient();
    }

    @Bean
    public AppConfigDataClient appConfigDataClient() {
        return mockAppConfigDataClient();
    }

    @Bean
    public SsmClient ssmClient() {
        return mockSsmClient();
    }
}
