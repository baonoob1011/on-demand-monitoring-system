package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.mission.dto.response.ReferenceCaptureResponse;

public interface IMissionReferenceCaptureService {

    /**
     * Captures a Mapillary reference image near the mission drone's latest telemetry position and stores it
     * through the existing media pipeline. The client never supplies coordinates.
     */
    ReferenceCaptureResponse captureMapillaryReference(String missionId);
}
