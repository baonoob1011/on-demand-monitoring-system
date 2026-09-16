package com.ondemandmonitoring.controlgateway.exception;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

public class ControlGatewayException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public ControlGatewayException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public static ControlGatewayException fromGrpc(StatusRuntimeException exception) {
        Status.Code status = exception.getStatus().getCode();
        int httpStatus = switch (status) {
            case UNAUTHENTICATED -> 401;
            case PERMISSION_DENIED -> 403;
            case NOT_FOUND -> 404;
            case ALREADY_EXISTS, ABORTED -> 409;
            case FAILED_PRECONDITION, INVALID_ARGUMENT, OUT_OF_RANGE -> 422;
            case UNAVAILABLE -> 503;
            case DEADLINE_EXCEEDED -> 504;
            default -> 502;
        };
        String message = exception.getStatus().getDescription();
        return new ControlGatewayException(
                "FLIGHT_CONTROLLER_" + status.name(),
                message == null || message.isBlank() ? "Flight Controller request failed" : message,
                httpStatus);
    }
}
