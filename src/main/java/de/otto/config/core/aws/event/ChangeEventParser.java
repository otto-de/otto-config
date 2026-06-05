package de.otto.config.core.aws.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.core.source.SourceChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ChangeEventParser {

    private final ObjectMapper objectMapper;

    public SourceChangeEvent parse(String messageBody) throws Exception {
        JsonNode root = objectMapper.readTree(messageBody);
        String source = root.path("source").asText("");
        String detailType = root.path("detail-type").asText("");
        JsonNode detail = root.path("detail");

        return switch (source) {
            case "aws.appconfig" -> parseAppConfigEvent(source, detailType, detail);
            case "aws.secretsmanager" -> parseSecretsManagerEvent(source, detailType, detail);
            case "aws.ssm" -> parseSsmEvent(source, detailType, detail);
            default -> {
                log.debug("Received EventBridge event from unrecognised source '{}' (detail-type: {})", source, detailType);
                yield new UnknownChangeEvent(source, detailType);
            }
        };
    }

    private AppConfigDeploymentEvent parseAppConfigEvent(String source, String detailType, JsonNode detail) {
        // Regions that support native AppConfig EventBridge events use
        // detail-type "AWS AppConfig Deployment Status" with top-level fields.
        // Other regions (e.g. eu-central-1) deliver CloudTrail management events
        // with detail-type "AWS API Call via CloudTrail"; the IDs are nested under
        // requestParameters / responseElements.
        if ("AWS API Call via CloudTrail".equals(detailType)) {
            JsonNode params = detail.path("requestParameters");
            JsonNode response = detail.path("responseElements");
            return new AppConfigDeploymentEvent(
                    source,
                    detailType,
                    params.path("applicationId").asText(""),
                    "", // application name is not present in CloudTrail events
                    params.path("environmentId").asText(""),
                    "",
                    params.path("configurationProfileId").asText(""),
                    response.path("configurationName").asText("") // profile name, e.g. "properties"
            );
        }
        // Native "AWS AppConfig Deployment Status" event format
        return new AppConfigDeploymentEvent(
                source,
                detailType,
                detail.path("application-id").asText(""),
                detail.path("application-name").asText(""),
                detail.path("environment-id").asText(""),
                detail.path("environment-name").asText(""),
                detail.path("configuration-profile-id").asText(""),
                detail.path("configuration-profile-name").asText("")
        );
    }

    private SecretsManagerChangeEvent parseSecretsManagerEvent(String source, String detailType, JsonNode detail) {
        // Secrets Manager events arrive via CloudTrail; the secret identifier is
        // nested under detail.requestParameters.secretId
        String secretId = detail.path("requestParameters").path("secretId").asText("");
        return new SecretsManagerChangeEvent(source, detailType, secretId);
    }

    private SsmParameterChangeEvent parseSsmEvent(String source, String detailType, JsonNode detail) {
        return new SsmParameterChangeEvent(
                source,
                detailType,
                detail.path("name").asText(""),
                detail.path("operation").asText("")
        );
    }
}
