package com.ondemandmonitoring.controlgateway.service;

import com.ondemandmonitoring.controlgateway.client.PreviewStream;
import com.ondemandmonitoring.controlgateway.dto.CommandResponse;
import com.ondemandmonitoring.controlgateway.dto.HealthResponse;
import com.ondemandmonitoring.controlgateway.dto.LocalMediaResponse;

import java.util.Iterator;
import java.util.List;

public interface FlightControlService {
    HealthResponse health(String operatorAccessToken);

    CommandResponse captureImage(String commandId, String missionId, String droneId,
                                 String operatorAccessToken);

    CommandResponse startVideo(String commandId, String missionId, String droneId,
                               String operatorAccessToken);

    CommandResponse stopVideo(String commandId, String operatorAccessToken);

    List<LocalMediaResponse> listMedia(String missionId, String operatorAccessToken);

    CommandResponse discardMedia(String commandId, String localMediaId,
                                 String operatorAccessToken);

    CommandResponse uploadMedia(String commandId, String localMediaId,
                                String operatorAccessToken);

    Iterator<CommandResponse> watchCommand(String commandId, String operatorAccessToken);

    PreviewStream openPreview(String localMediaId, long offset, long length,
                              String operatorAccessToken);
}
