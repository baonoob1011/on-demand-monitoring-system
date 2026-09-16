package com.ondemandmonitoring.controlgateway.client;

import monitoring.flightcontroller.v1.Media;

import java.util.Iterator;
import java.util.List;

public interface FlightControllerClient {
    Media.FlightControllerHealth health(String operatorAccessToken);

    Media.MediaCommandAck captureImage(String commandId, String missionId, String droneId,
                                       String operatorAccessToken);

    Media.MediaCommandAck startVideo(String commandId, String missionId, String droneId,
                                    String operatorAccessToken);

    Media.MediaCommandAck stopVideo(String commandId, String operatorAccessToken);

    List<Media.LocalMedia> listMedia(String missionId, String operatorAccessToken);

    Media.MediaCommandAck discardMedia(String commandId, String localMediaId,
                                       String operatorAccessToken);

    Media.MediaCommandAck uploadMedia(String commandId, String localMediaId,
                                      String operatorAccessToken);

    Iterator<Media.MediaCommandUpdate> watchCommand(String commandId, String operatorAccessToken);

    PreviewStream openPreview(String localMediaId, long offset, long length,
                              String operatorAccessToken);
}
