package com.ondemandmonitoring.Consultation.dtos.responses;

public record ServiceSearchCandidate(
        String serviceId,
        String serviceName,
        String content,
        double score
) {
}
