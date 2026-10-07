package com.ondemandmonitoring.flightarea.support;

/**
 * An external data provider (elevation, Overpass) failed or answered with something unusable.
 * Carries a stable code only; raw provider text is never put into API responses.
 */
public class ExternalProviderException extends RuntimeException {

    public static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    public static final String PROVIDER_BAD_RESPONSE = "PROVIDER_BAD_RESPONSE";

    private final String errorCode;

    public ExternalProviderException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ExternalProviderException(String errorCode, String message) {
        this(errorCode, message, null);
    }

    public String getErrorCode() {
        return errorCode;
    }
}
