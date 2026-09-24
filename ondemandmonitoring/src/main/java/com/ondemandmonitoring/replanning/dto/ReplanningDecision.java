package com.ondemandmonitoring.replanning.dto;

import com.ondemandmonitoring.replanning.domain.ReplanningReason;

public record ReplanningDecision(
        boolean required,
        ReplanningReason reason,
        String detail) {

    public static ReplanningDecision none(String detail) {
        return new ReplanningDecision(false, null, detail);
    }

    public static ReplanningDecision required(ReplanningReason reason, String detail) {
        return new ReplanningDecision(true, reason, detail);
    }
}
