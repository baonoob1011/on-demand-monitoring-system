package com.ondemandmonitoring.device.dto.response;

import com.ondemandmonitoring.device.domain.DeviceImage;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
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

