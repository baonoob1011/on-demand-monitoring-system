package com.ondemandmonitoring.mission.domain;

/** Mission aggregate created from an approved monitoring request. */
public record Mission(String id, String monitoringRequestId) {
}
