package de.otto.config.core.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.core.metrics.ConfigMetrics;
import de.otto.config.core.metrics.ConfigMetricsRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class RestClientTest {

    static class DummyResponse {
        public String message;
    }

    private HttpClient mockHttpClient;
    private ObjectMapper objectMapper;
    private RestClient<DummyResponse> restClient;
    private List<String> recordedHttpRequests;

    @AfterEach
    void tearDown() {
        ConfigMetricsRegistry.reset();
    }

    @BeforeEach
    void setUp() throws Exception{
        mockHttpClient = mock(HttpClient.class);
        objectMapper = mock(ObjectMapper.class);
        restClient = new RestClient<DummyResponse>(DummyResponse.class, objectMapper);
        var clientField = RestClient.class.getDeclaredField("httpClient");
        clientField.setAccessible(true);
        clientField.set(restClient, mockHttpClient);

        recordedHttpRequests = new ArrayList<>();
        ConfigMetricsRegistry.setEnabled(true);
        ConfigMetricsRegistry.register(new ConfigMetrics() {
            @Override
            public void httpRequest(String client, String method, int status, Duration duration) {
                recordedHttpRequests.add(client + ":" + method + ":" + status);
            }
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldPerformGetRequestAndReturnParsedResponse() throws Exception {
        // given
        HttpResponse<Object> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"message\":\"hello\"}");
        when(mockHttpClient.send(any(), any())).thenReturn(response);

        DummyResponse dummy = new DummyResponse();
        dummy.message = "hello";
        when(objectMapper.readValue("{\"message\":\"hello\"}", DummyResponse.class)).thenReturn(dummy);

        // when
        DummyResponse result = restClient.get("http://test.com", Map.of("Authorization", "Bearer token"));

        // then
        assertThat(result.message, is("hello"));
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        HttpRequest req = captor.getValue();
        assertThat(req.uri(), is(URI.create("http://test.com")));
        assertThat(req.headers().firstValue("Authorization").orElse(""), is("Bearer token"));
        assertThat(recordedHttpRequests, hasItem("RestClient:GET:200"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldPerformPostRequestWithBodyAndReturnParsedResponse() throws Exception {
        // given
        HttpResponse<Object> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"message\":\"posted\"}");
        when(mockHttpClient.send(any(), any())).thenReturn(response);

        DummyResponse dummy = new DummyResponse();
        dummy.message = "posted";
        when(objectMapper.readValue("{\"message\":\"posted\"}", DummyResponse.class)).thenReturn(dummy);

        // when
        DummyResponse result = restClient.post("http://test.com", "{\"foo\":\"bar\"}", Map.of("Content-Type", "application/json"));

        // then
        assertThat(result.message, is("posted"));
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(captor.capture(), any());
        HttpRequest req = captor.getValue();
        assertThat(req.uri(), is(URI.create("http://test.com")));
        assertThat(req.headers().firstValue("Content-Type").orElse(""), is("application/json"));
        assertThat(recordedHttpRequests, hasItem("RestClient:POST:200"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldThrowRestExceptionOnNon200Status() throws Exception {
        // given
        HttpResponse<Object> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);
        when(response.body()).thenReturn("Not found");
        when(mockHttpClient.send(any(), any())).thenReturn(response);

        // when/then
        RestException ex = assertThrows(RestException.class, () ->
                restClient.get("http://test.com", Map.of())
        );
        assertThat(ex.getMessage(), containsString("Unexpected status: 404"));
        assertThat(ex.getMessage(), containsString("GET http://test.com"));
        assertThat(ex.getMessage(), containsString("body='Not found'"));
        assertThat(ex.getStatusCode(), is(404));
        assertThat(ex.getResponse(), is(response));
        assertThat(recordedHttpRequests, hasItem("RestClient:GET:404"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldIncludeDiagnosticHeadersAndTruncatedBodyOnNon200Status() throws Exception {
        // given
        HttpResponse<Object> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(502);
        when(response.body()).thenReturn("<html>\n<body>502 Bad Gateway</body>\n</html>" + "x".repeat(1000));
        when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(
                Map.of("Server", List.of("awselb/2.0"), "X-Amzn-Trace-Id", List.of("Root=1-abc")), (a, b) -> true));
        when(mockHttpClient.send(any(), any())).thenReturn(response);

        // when/then
        RestException ex = assertThrows(RestException.class, () ->
                restClient.get("http://test.com/v1/secret", Map.of("X-Vault-Token", "s.secret"))
        );
        assertThat(ex.getMessage(), containsString("Unexpected status: 502"));
        assertThat(ex.getMessage(), containsString("GET http://test.com/v1/secret"));
        assertThat(ex.getMessage(), containsString("server=awselb/2.0"));
        assertThat(ex.getMessage(), containsString("x-amzn-trace-id=Root=1-abc"));
        assertThat(ex.getMessage(), containsString("body='<html> <body>502 Bad Gateway</body> </html>xxx"));
        assertThat(ex.getMessage(), containsString("...'"));
        assertThat(ex.getMessage(), not(containsString("s.secret")));
    }

    @Test
    void shouldThrowRestExceptionOnSendFailure() throws Exception {
        // given
        when(mockHttpClient.send(any(), any())).thenThrow(new RuntimeException("Network error"));

        // when/then
        RestException ex = assertThrows(RestException.class, () ->
                restClient.get("http://test.com", Map.of())
        );
        assertThat(ex.getMessage(), containsString("Failed to handle request"));
        assertThat(ex.getCause().getMessage(), is("Network error"));
        assertThat(recordedHttpRequests, hasItem("RestClient:GET:-1"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldPerformPostRequestWithoutBody() throws Exception {
        // given
        HttpResponse<Object> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"message\":\"empty body\"}");
        when(mockHttpClient.send(any(), any())).thenReturn(response);

        DummyResponse dummy = new DummyResponse();
        dummy.message = "empty body";
        when(objectMapper.readValue("{\"message\":\"empty body\"}", DummyResponse.class)).thenReturn(dummy);

        // when
        DummyResponse result = restClient.post("http://test.com", Map.of());

        // then
        assertThat(result.message, is("empty body"));
    }
}
