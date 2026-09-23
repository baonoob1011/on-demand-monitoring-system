package com.ondemandmonitoring.common.exception;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.openai.errors.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.InterruptedIOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(
            ApiException exception
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiResponse.error(
                        exception.getErrorCode().name(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError fieldError
                : exception.getBindingResult().getFieldErrors()) {
            errors.put(
                    fieldError.getField(),
                    fieldError.getDefaultMessage()
            );
        }

        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.error(
                        ErrorCode.VALIDATION_ERROR.name(),
                        ErrorCode.VALIDATION_ERROR.getMessage(),
                        errors
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException exception
    ) {
        log.debug(
                "No endpoint found for {} {}",
                exception.getHttpMethod(),
                exception.getResourcePath()
        );

        return ResponseEntity
                .status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ApiResponse.error(
                        ErrorCode.RESOURCE_NOT_FOUND.name(),
                        ErrorCode.RESOURCE_NOT_FOUND.getMessage()
                ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception
    ) {
        log.debug(
                "HTTP method {} is not supported. Supported methods: {}",
                exception.getMethod(),
                exception.getSupportedHttpMethods()
        );

        return ResponseEntity
                .status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ApiResponse.error(
                        ErrorCode.METHOD_NOT_ALLOWED.name(),
                        ErrorCode.METHOD_NOT_ALLOWED.getMessage()
                ));
    }
    @ExceptionHandler(InterruptedIOException.class)
    public ResponseEntity<ApiResponse<Void>> handleInterruptedIOException(
            InterruptedIOException ex,
            HttpServletRequest request
    ) {
        log.warn(
                "Request interrupted: {} {} - {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage()
        );

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .code("REQUEST_INTERRUPTED")
                .message("Yêu cầu đã bị gián đoạn")
                .success(false)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity
                .status(HttpStatus.REQUEST_TIMEOUT)
                .body(response);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiNotFoundException(
            com.openai.errors.NotFoundException exception
    ) {
        log.warn(
                "AI provider returned 404 Not Found: {}",
                exception.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        "AI_RESOURCE_NOT_FOUND",
                        "AI provider resource or endpoint was not found"
                ));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (isClientDisconnect(exception)) {
            log.debug(
                    "Client disconnected while streaming media: {} {}",
                    request.getMethod(),
                    request.getRequestURI()
            );
            return null;
        }

        if (response.isCommitted()) {
            log.warn(
                    "Response already committed, cannot send JSON error response "
                            + "for: {} {}. Error: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    exception.getMessage()
            );
            return null;
        }

        log.error(
                "Unexpected API error: {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(
                        ErrorCode.INTERNAL_SERVER_ERROR.name(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                ));
    }

    private boolean isClientDisconnect(Throwable exception) {
        Throwable cause = exception;

        while (cause != null) {
            String className = cause.getClass().getName();

            String message = cause.getMessage() != null
                    ? cause.getMessage().toLowerCase()
                    : "";

            if (className.contains("ClientAbortException")
                    || message.contains("broken pipe")
                    || message.contains("connection reset by peer")
                    || message.contains(
                    "an established connection was aborted "
                            + "by the software in your host machine"
            )) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}