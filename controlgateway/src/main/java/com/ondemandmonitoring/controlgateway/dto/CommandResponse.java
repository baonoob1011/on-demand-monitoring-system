package com.ondemandmonitoring.controlgateway.dto;

public record CommandResponse(
        String commandId,
        String state,
        LocalMediaResponse media,
        String errorCode,
        String errorMessage
) {
    public boolean terminal() {
        return "SUCCEEDED".equals(state) || "FAILED".equals(state);
    }
}
