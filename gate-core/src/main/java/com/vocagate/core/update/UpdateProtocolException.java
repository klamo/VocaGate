package com.vocagate.core.update;

public final class UpdateProtocolException extends RuntimeException {
    public UpdateProtocolException(String message) {
        super(message);
    }

    public UpdateProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
