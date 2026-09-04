package com.ondemandmonitoring.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.status = errorCode.getStatus();
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = errorCode.getStatus();
    }

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.errorCode = status.is5xxServerError()
                ? ErrorCode.INTERNAL_SERVER_ERROR
                : ErrorCode.INVALID_REQUEST;
        this.status = status;
    }
}
