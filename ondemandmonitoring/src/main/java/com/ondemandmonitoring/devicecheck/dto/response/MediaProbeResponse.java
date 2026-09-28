package com.ondemandmonitoring.devicecheck.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MediaProbeResponse {

    String status;
    String runId;
    String missionId;
    String deviceId;
    String contentType;
    long sizeBytes;
    boolean checksumVerified;
    boolean storageVerified;
    boolean cleanupVerified;
    String message;
    Instant checkedAt;
}
