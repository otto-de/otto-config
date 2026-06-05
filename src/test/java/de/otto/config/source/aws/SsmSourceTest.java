package de.otto.config.source.aws;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasEntry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.otto.config.core.source.Source;
import de.otto.config.domain.Properties;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.paginators.GetParametersByPathIterable;

import static org.hamcrest.Matchers.is;
import java.util.Collections;

public class SsmSourceTest {

    @Mock
    private SsmClient ssmClient;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void shouldLoadPropertiesFromSsm() {
        // given
        GetParametersByPathRequest request = GetParametersByPathRequest.builder().path("/").build();
        GetParametersByPathIterable paginator = new GetParametersByPathIterable(ssmClient, request);
        GetParametersByPathResponse response = GetParametersByPathResponse.builder()
                .parameters(
                        Parameter.builder()
                                 .name("/search/develop/loki/config/service.url")
                                 .value("https://deinemudder.de")
                                 .build(),
                        Parameter.builder()
                                 .name("/search/develop/ash/config/service.url")
                                 .value("https://deineanderemudder.de")
                                 .build(),
                        Parameter.builder()
                                 .name("/search/develop/zealot/config/service.url")
                                 .value("https://otto.config.de")
                                 .build(),
                        Parameter.builder()
                                 .name("/cassandra/otto_api_credentials.json")
                                 .value("supergeheim")
                                 .build())
                .build();

        when(ssmClient.getParametersByPathPaginator(any(GetParametersByPathRequest.class))).thenReturn(paginator);
        when(ssmClient.getParametersByPath(any(GetParametersByPathRequest.class))).thenReturn(response);

        Source<Properties> ssmPropertySource = SsmSource.builder()
                                                    .applicationIdentifier("zealot")
                                                    .ssmClient(ssmClient)
                                                    .ssmPathPrefix("/")
                                                    .build();

        // when
        Properties result = ssmPropertySource.getOrLoad();

        // then
        assertThat(result.getProperties(), aMapWithSize(7));
        assertThat(result.getProperties(), hasEntry("/search/develop/loki/config/service.url", "https://deinemudder.de"));
        assertThat(result.getProperties(), hasEntry("loki/service.url", "https://deinemudder.de"));
        assertThat(result.getProperties(), hasEntry("/search/develop/ash/config/service.url", "https://deineanderemudder.de"));
        assertThat(result.getProperties(), hasEntry("ash/service.url", "https://deineanderemudder.de"));
        assertThat(result.getProperties(), hasEntry("/search/develop/zealot/config/service.url", "https://otto.config.de"));
        assertThat(result.getProperties(), hasEntry("service.url", "https://otto.config.de"));
        assertThat(result.getProperties(), hasEntry("/cassandra/otto_api_credentials.json", "supergeheim"));
    }

    @Test
    public void shouldHandleNoParameters() {
        // given
        GetParametersByPathResponse response = GetParametersByPathResponse.builder()
                .parameters(Collections.emptyList())
                .build();

        when(ssmClient.getParametersByPath(any(GetParametersByPathRequest.class))).thenReturn(response);

        Source<Properties> ssmPropertySource = SsmSource.builder()
                                                        .applicationIdentifier("zealot")
                                                        .ssmClient(ssmClient)
                                                        .build();

        // when
        Properties result = ssmPropertySource.getOrLoad();

        // then
        assertThat(result.getProperties().isEmpty(), is(true));
    }
}

