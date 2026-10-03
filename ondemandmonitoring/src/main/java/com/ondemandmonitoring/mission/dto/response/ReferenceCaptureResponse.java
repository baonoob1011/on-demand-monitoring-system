package com.ondemandmonitoring.mission.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/** Result of a Mapillary reference-image capture. Never contains the Mapillary token. */
@Value
@Builder
public class ReferenceCaptureResponse {
    String mediaAssetId;
    String missionId;
    String sourceType;
    Position dronePosition;
    Position sourcePosition;
    Double distanceMeters;
    String mapillaryImageId;
    Instant capturedAt;
    String mediaUrl;
    boolean duplicate;

    @Value
    @Builder
    public static class Position {
        Double lat;
        Double lon;
        Double altitude;
    }
}
