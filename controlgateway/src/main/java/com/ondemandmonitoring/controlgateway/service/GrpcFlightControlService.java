package com.ondemandmonitoring.controlgateway.service;

import com.ondemandmonitoring.controlgateway.client.FlightControllerClient;
import com.ondemandmonitoring.controlgateway.client.PreviewStream;
import com.ondemandmonitoring.controlgateway.client.ControlAuthorizationClient;
import com.ondemandmonitoring.controlgateway.client.ControlAuthorizationContext;
import com.ondemandmonitoring.controlgateway.dto.CommandResponse;
import com.ondemandmonitoring.controlgateway.dto.HealthResponse;
import com.ondemandmonitoring.controlgateway.dto.LocalMediaResponse;
import monitoring.flightcontroller.v1.Media;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;

@Service
public class GrpcFlightControlService implements FlightControlService {

    private final FlightControllerClient client;
    private final ControlAuthorizationClient authorizationClient;

    public GrpcFlightControlService(FlightControllerClient client,
                                    ControlAuthorizationClient authorizationClient) {
        this.client = client;
        this.authorizationClient = authorizationClient;
    }

    @Override
    public HealthResponse health(String operatorAccessToken) {
        Media.FlightControllerHealth health = client.health(operatorAccessToken);
        return new HealthResponse(health.getStatus(), health.getReady(), health.getRecording(), Instant.now());
    }

    @Override
    public CommandResponse captureImage(String commandId, String missionId, String droneId,
                                        String operatorAccessToken) {
        requireCapture(missionId, droneId, operatorAccessToken);
        return map(client.captureImage(commandId, missionId, droneId, operatorAccessToken));
    }

    @Override
    public CommandResponse startVideo(String commandId, String missionId, String droneId,
                                      String operatorAccessToken) {
        requireCapture(missionId, droneId, operatorAccessToken);
        return map(client.startVideo(commandId, missionId, droneId, operatorAccessToken));
    }

    @Override
    public CommandResponse stopVideo(String commandId, String missionId, String droneId,
                                     String operatorAccessToken) {
        requireCapture(missionId, droneId, operatorAccessToken);
        return map(client.stopVideo(commandId, missionId, droneId, operatorAccessToken));
    }

    @Override
    public List<LocalMediaResponse> listMedia(String missionId, String operatorAccessToken) {
        requireControl(missionId, null, operatorAccessToken);
        return client.listMedia(missionId, operatorAccessToken).stream().map(this::map).toList();
    }

    @Override
    public CommandResponse discardMedia(String commandId, String localMediaId,
                                        String operatorAccessToken) {
        authorizeLocalMedia(localMediaId, operatorAccessToken, false);
        return map(client.discardMedia(commandId, localMediaId, operatorAccessToken));
    }

    @Override
    public CommandResponse uploadMedia(String commandId, String localMediaId,
                                       String operatorAccessToken) {
        authorizeLocalMedia(localMediaId, operatorAccessToken, true);
        return map(client.uploadMedia(commandId, localMediaId, operatorAccessToken));
    }

    @Override
    public Iterator<CommandResponse> watchCommand(String commandId, String operatorAccessToken) {
        Iterator<Media.MediaCommandUpdate> source = client.watchCommand(commandId, operatorAccessToken);
        return new Iterator<>() {
            public boolean hasNext() { return source.hasNext(); }
            public CommandResponse next() { return map(source.next()); }
        };
    }

    @Override
    public PreviewStream openPreview(String localMediaId, long offset, long length,
                                     String operatorAccessToken) {
        authorizeLocalMedia(localMediaId, operatorAccessToken, false);
        return client.openPreview(localMediaId, offset, length, operatorAccessToken);
    }

    private void requireCapture(String missionId, String droneId, String token) {
        ControlAuthorizationContext context = authorizationClient.authorize(missionId, droneId, token);
        if (!context.mediaCaptureAllowed()) {
            throw new com.ondemandmonitoring.controlgateway.exception.ControlGatewayException(
                    "MISSION_STATE_INVALID", "Mission does not allow media capture", 409);
        }
    }

    private void requireControl(String missionId, String droneId, String token) {
        ControlAuthorizationContext context = authorizationClient.authorize(missionId, droneId, token);
        if (!context.controlAllowed()) {
            throw new com.ondemandmonitoring.controlgateway.exception.ControlGatewayException(
                    "MISSION_STATE_INVALID", "Mission does not allow drone control", 409);
        }
    }

    private void authorizeLocalMedia(String localMediaId, String token, boolean upload) {
        Media.LocalMedia media = client.listMedia("", token).stream()
                .filter(item -> item.getLocalMediaId().equals(localMediaId))
                .findFirst()
                .orElseThrow(() -> new com.ondemandmonitoring.controlgateway.exception.ControlGatewayException(
                        "MEDIA_NOT_FOUND", "Local media does not exist", 404));
        ControlAuthorizationContext context = authorizationClient.authorize(
                media.getMissionId(), media.getDroneId(), token);
        boolean allowed = upload ? context.mediaUploadAllowed() : context.controlAllowed();
        if (!allowed) {
            throw new com.ondemandmonitoring.controlgateway.exception.ControlGatewayException(
                    "MISSION_STATE_INVALID", "Mission state does not allow this media operation", 409);
        }
    }

    private CommandResponse map(Media.MediaCommandAck source) {
        return new CommandResponse(source.getCommandId(), enumName(source.getState().name(), "COMMAND_STATE_"),
                source.hasMedia() ? map(source.getMedia()) : null,
                source.getErrorCode(), source.getErrorMessage());
    }

    private CommandResponse map(Media.MediaCommandUpdate source) {
        return new CommandResponse(source.getCommandId(), enumName(source.getState().name(), "COMMAND_STATE_"),
                source.hasMedia() ? map(source.getMedia()) : null,
                source.getErrorCode(), source.getErrorMessage());
    }

    private LocalMediaResponse map(Media.LocalMedia source) {
        String id = source.getLocalMediaId();
        return new LocalMediaResponse(
                id,
                source.getMissionId(),
                source.getDroneId(),
                enumName(source.getMediaType().name(), "MEDIA_TYPE_"),
                enumName(source.getStatus().name(), "LOCAL_MEDIA_STATUS_"),
                source.getFileName(),
                source.getContentType(),
                source.getFileSize(),
                source.getChecksumSha256(),
                source.getCapturedAt(),
                source.getBackendMediaId(),
                source.getErrorCode(),
                source.getErrorMessage(),
                "/api/control/v1/media/" + id + "/preview");
    }

    private String enumName(String value, String prefix) {
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }
}
