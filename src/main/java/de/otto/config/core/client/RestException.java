package de.otto.config.core.client;

import java.net.http.HttpResponse;

import lombok.Getter;

@Getter
public class RestException extends Exception {
    private final HttpResponse<?> response;

    public RestException(String message) {
        super(message);
        this.response = null;
    }

    public RestException(String message, HttpResponse<?> response) {
        super(message);
        this.response = response;
    }

    public RestException(String message, Throwable cause) {
        super(message, cause);
        this.response = null;
    }
}
