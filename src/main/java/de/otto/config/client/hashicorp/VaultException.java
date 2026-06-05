package de.otto.config.client.hashicorp;

public class VaultException extends Exception {
    public VaultException(String message) {
        super(message);
    }

    public VaultException(String message, Throwable cause) {
        super(message, cause);
    }
}
