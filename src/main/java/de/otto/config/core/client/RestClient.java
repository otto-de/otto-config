package de.otto.config.core.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RestClient<T> {
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
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RestException("Unexpected status: " + response.statusCode(), response);
            }

            return objectMapper.readValue(response.body(), this.type);
        } catch (RestException e) {
            throw e;
        } catch (Exception e) {
            throw new RestException("Failed to handle request: " + e.getMessage(), e);
        }
    }
}
