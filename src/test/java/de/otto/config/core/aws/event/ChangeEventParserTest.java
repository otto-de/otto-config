package de.otto.config.core.aws.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.otto.config.core.source.SourceChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class ChangeEventParserTest {

    private ChangeEventParser parser;

    @BeforeEach
    void setUp() {
        parser = new ChangeEventParser(new ObjectMapper());
    }

    @Test
    void shouldParseNativeAppConfigEvent() throws Exception {
        // given
        String body = """
                {
                  "source": "aws.appconfig",
                  "detail-type": "AWS AppConfig Deployment Status",
                  "detail": {
                    "application-id": "app123",
                    "application-name": "my-app",
                    "environment-id": "env456",
                    "environment-name": "production",
                    "configuration-profile-id": "prof789",
                    "configuration-profile-name": "properties"
                  }
                }
                """;

        // when
        SourceChangeEvent event = parser.parse(body);

        // then
        assertThat(event, instanceOf(AppConfigDeploymentEvent.class));
        AppConfigDeploymentEvent appConfigEvent = (AppConfigDeploymentEvent) event;
        assertThat(appConfigEvent.source(), is("aws.appconfig"));
        assertThat(appConfigEvent.detailType(), is("AWS AppConfig Deployment Status"));
        assertThat(appConfigEvent.applicationId(), is("app123"));
        assertThat(appConfigEvent.applicationName(), is("my-app"));
        assertThat(appConfigEvent.environmentId(), is("env456"));
        assertThat(appConfigEvent.environmentName(), is("production"));
        assertThat(appConfigEvent.configProfileId(), is("prof789"));
        assertThat(appConfigEvent.configProfileName(), is("properties"));
    }

    @Test
    void shouldParseAppConfigCloudTrailEvent() throws Exception {
        // given – CloudTrail wraps IDs differently; application name is absent
        String body = """
                {
                  "source": "aws.appconfig",
                  "detail-type": "AWS API Call via CloudTrail",
                  "detail": {
                    "requestParameters": {
                      "applicationId": "app123",
                      "environmentId": "env456",
                      "configurationProfileId": "prof789"
                    },
                    "responseElements": {
                      "configurationName": "properties"
                    }
                  }
                }
                """;

        // when
        SourceChangeEvent event = parser.parse(body);

        // then
        assertThat(event, instanceOf(AppConfigDeploymentEvent.class));
        AppConfigDeploymentEvent appConfigEvent = (AppConfigDeploymentEvent) event;
        assertThat(appConfigEvent.applicationId(), is("app123"));
        assertThat(appConfigEvent.applicationName(), is("")); // not available in CloudTrail
        assertThat(appConfigEvent.environmentId(), is("env456"));
        assertThat(appConfigEvent.configProfileId(), is("prof789"));
        assertThat(appConfigEvent.configProfileName(), is("properties"));
    }

    @Test
    void shouldParseSecretsManagerEvent() throws Exception {
        // given
        String body = """
                {
                  "source": "aws.secretsmanager",
                  "detail-type": "AWS API Call via CloudTrail",
                  "detail": {
                    "requestParameters": {
                      "secretId": "arn:aws:secretsmanager:eu-west-1:123456789:secret:my-secret"
                    }
                  }
                }
                """;

        // when
        SourceChangeEvent event = parser.parse(body);

        // then
        assertThat(event, instanceOf(SecretsManagerChangeEvent.class));
        SecretsManagerChangeEvent smEvent = (SecretsManagerChangeEvent) event;
        assertThat(smEvent.source(), is("aws.secretsmanager"));
        assertThat(smEvent.secretId(), is("arn:aws:secretsmanager:eu-west-1:123456789:secret:my-secret"));
    }

    @Test
    void shouldParseSsmParameterEvent() throws Exception {
        // given
        String body = """
                {
                  "source": "aws.ssm",
                  "detail-type": "Parameter Store Change",
                  "detail": {
                    "name": "/my-app/production/db.url",
                    "operation": "Update"
                  }
                }
                """;

        // when
        SourceChangeEvent event = parser.parse(body);

        // then
        assertThat(event, instanceOf(SsmParameterChangeEvent.class));
        SsmParameterChangeEvent ssmEvent = (SsmParameterChangeEvent) event;
        assertThat(ssmEvent.source(), is("aws.ssm"));
        assertThat(ssmEvent.detailType(), is("Parameter Store Change"));
        assertThat(ssmEvent.parameterName(), is("/my-app/production/db.url"));
        assertThat(ssmEvent.operation(), is("Update"));
    }

    @Test
    void shouldReturnUnknownEventForUnrecognisedSource() throws Exception {
        // given
        String body = """
                {
                  "source": "aws.ec2",
                  "detail-type": "EC2 Instance State-change Notification",
                  "detail": {}
                }
                """;

        // when
        SourceChangeEvent event = parser.parse(body);

        // then
        assertThat(event, instanceOf(UnknownChangeEvent.class));
        assertThat(event.source(), is("aws.ec2"));
        assertThat(event.detailType(), is("EC2 Instance State-change Notification"));
    }

    @Test
    void shouldHandleMissingFieldsGracefully() throws Exception {
        // given – minimal / empty event
        String body = """
                {
                  "source": "aws.ssm",
                  "detail-type": "Parameter Store Change",
                  "detail": {}
                }
                """;

        // when
        SourceChangeEvent event = parser.parse(body);

        // then – no exception; missing fields default to empty string
        assertThat(event, instanceOf(SsmParameterChangeEvent.class));
        SsmParameterChangeEvent ssmEvent = (SsmParameterChangeEvent) event;
        assertThat(ssmEvent.parameterName(), is(""));
        assertThat(ssmEvent.operation(), is(""));
    }
}
