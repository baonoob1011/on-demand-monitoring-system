package com.ondemandmonitoring.controlgateway.exception;

import java.time.Instant;

public record ErrorResponse(boolean success, String code, String message, Instant timestamp) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(false, code, message, Instant.now());
    }
}
