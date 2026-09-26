package com.ondemandmonitoring.media.dto.response;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MediaResponse {

    String id;
    String missionId;
    String droneId;
    String type;
    String url;
    Long expiresIn;
    String contentType;
    Long fileSize;
    Instant capturedAt;
}
