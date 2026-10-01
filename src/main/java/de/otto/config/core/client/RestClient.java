package de.otto.config.core.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.otto.config.core.metrics.ConfigMetricsRegistry;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RestClient<T> {
    private static final int MAX_BODY_LENGTH = 512;
    private static final List<String> DIAGNOSTIC_HEADERS = List.of("server", "via", "content-type", "x-request-id", "x-amzn-requestid", "x-amzn-trace-id", "x-amz-cf-id");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    final @NonNull Class<T> type;
    final @NonNull ObjectMapper objectMapper;

    public T get(String url, Map<String, String> headers) throws RestException {
       HttpRequest.Builder builder = HttpRequest.newBuilder()
                                                .uri(URI.create(url))
                                                .GET();
       headers.forEach(builder::header);
       HttpRequest request = builder.build();

       return sendRequest(request);
    }

    public T post(String url, Map<String, String> headers) throws RestException {
       return post(url, null, headers);
    }

    public T post(String url, String body, Map<String, String> headers) throws RestException {
       HttpRequest.Builder builder = HttpRequest.newBuilder()
                                                .uri(URI.create(url))
                                                .POST(body != null && !body.isEmpty() ? HttpRequest.BodyPublishers.ofString(body) 
                                                                                      : HttpRequest.BodyPublishers.noBody());
       headers.forEach(builder::header);
       HttpRequest request = builder.build();

       return sendRequest(request);
    }

    private T sendRequest(HttpRequest request) throws RestException {
        long start = System.nanoTime();
        int status = -1;
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RestException("Unexpected status: " + response.statusCode()
                                        + " [" + describe(request, start) + diagnosticHeaders(response)
                                        + ", body='" + abbreviate(response.body()) + "']", response);
            }

            return objectMapper.readValue(response.body(), this.type);
        } catch (RestException e) {
            throw e;
        } catch (Exception e) {
            throw new RestException("Failed to handle request: " + e.getMessage() + " [" + describe(request, start) + "]", e);
        } finally {
            Duration duration = Duration.ofNanos(System.nanoTime() - start);
            String client = getClass().getSimpleName();
            ConfigMetricsRegistry.get().httpRequest(client.isEmpty() ? "RestClient" : client, request.method(), status, duration);
        }
    }

    private static String describe(HttpRequest request, long startNanos) {
        return request.method() + " " + request.uri() + ", elapsed=" + (System.nanoTime() - startNanos) / 1_000_000 + "ms";
    }

    private static String diagnosticHeaders(HttpResponse<?> response) {
        StringBuilder sb = new StringBuilder();
        if (response.headers() == null) {
            return "";
        }
        for (String name : DIAGNOSTIC_HEADERS) {
            response.headers().firstValue(name).ifPresent(value -> sb.append(", ").append(name).append('=').append(value));
        }
        return sb.toString();
    }

    // Only called for non-2xx responses, which never carry secret payloads
    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String singleLine = body.replaceAll("\\s+", " ").trim();
        return singleLine.length() > MAX_BODY_LENGTH ? singleLine.substring(0, MAX_BODY_LENGTH) + "..." : singleLine;
    }
}
