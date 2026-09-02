package de.otto.config.source.aws;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.otto.config.core.aws.event.SsmParameterChangeEvent;
import de.otto.config.core.source.SourceChangeEvent;
import de.otto.config.core.source.SourceException;
import de.otto.config.domain.Properties;
import de.otto.config.source.PropertySource;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.paginators.GetParametersByPathIterable;

@Builder
@RequiredArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@Slf4j
public class SsmSource extends PropertySource {
    private final static Pattern LEGACY_FORMAT = Pattern.compile("/[^/]+/[^/]+/([^/]+)/[^/]+/(.+)");

    private final @NonNull String applicationIdentifier;
    private final @NonNull SsmClient ssmClient;
    @Builder.Default
    private final @NonNull String ssmPathPrefix = "/";
    @Builder.Default
    private final boolean isPullRefreshEnabled = true;
    private final boolean excludeSecrets;

    @Override
    public boolean isPullRefreshEnabled() {
        return this.isPullRefreshEnabled;
    }
    
    @Override
    public boolean onChanged(SourceChangeEvent event) {
        if (!(event instanceof SsmParameterChangeEvent e)) {
            return false;
        }
        return e.parameterName().startsWith(this.ssmPathPrefix);
    }

    @Override
    public Properties load() throws SourceException {
        log.debug("Reading SSM parameter from {}", this.ssmPathPrefix);

        try {
            Map<String, String> properties = new HashMap<>();
            GetParametersByPathIterable paginator = this.ssmClient
                    .getParametersByPathPaginator((GetParametersByPathRequest) GetParametersByPathRequest.builder()
                            .path(this.ssmPathPrefix)
                            .withDecryption(true)
                            .recursive(true).build());
            paginator.stream().flatMap((page) -> {
                return page.parameters().stream();
            }).forEach((param) -> {
                log.debug("Read SSM parameter: name='{}', valuePrefix='{}...'", param.name(), firstThreeChars(param.value()));
                if (this.excludeSecrets && param.type() == ParameterType.SECURE_STRING) {
                    return; // exclude this parameter entirely
                }
                properties.put(param.name(), param.value());

                addServiceLevelProperty(properties, param);
            });
            return new Properties(properties);
        } catch (Exception e) {
            log.debug("Exception reading ssm {}", e.getMessage());
            throw new SourceException("Could not load Otto Config properties from SSM", e);
        }
    }

    private static String firstThreeChars(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 3 ? value : value.substring(0, 3);
    }

    protected void addServiceLevelProperty(Map<String, String> properties, Parameter param) {
        if (param.name().contains("/config/")) {
            final Matcher m = LEGACY_FORMAT.matcher(param.name());
            if (m.find()) {
                final String service = m.group(1);
                final String key = m.group(2);
                
                if (service.equals(applicationIdentifier)) {
                    properties.put(key, param.value());
                } else {
                    properties.put(String.format("%s/%s", service, key), param.value());
                }
            }
        }
    }
}
