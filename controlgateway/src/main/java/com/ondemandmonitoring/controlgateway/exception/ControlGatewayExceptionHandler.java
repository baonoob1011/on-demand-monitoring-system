package com.ondemandmonitoring.controlgateway.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ControlGatewayExceptionHandler {

    @ExceptionHandler(ControlGatewayException.class)
    ResponseEntity<ErrorResponse> handleControlGateway(ControlGatewayException exception) {
        return ResponseEntity.status(exception.httpStatus())
                .body(ErrorResponse.of(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_REQUEST", message));
    }
}
