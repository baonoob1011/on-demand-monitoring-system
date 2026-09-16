package com.ondemandmonitoring.controlgateway.service;

import com.ondemandmonitoring.controlgateway.client.ControlAuthorizationClient;
import com.ondemandmonitoring.controlgateway.client.ControlAuthorizationContext;
import com.ondemandmonitoring.controlgateway.client.FlightControllerClient;
import com.ondemandmonitoring.controlgateway.dto.CommandResponse;
import com.ondemandmonitoring.controlgateway.exception.ControlGatewayException;
import monitoring.flightcontroller.v1.Media;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrpcFlightControlServiceTest {

    @Mock
    private FlightControllerClient client;
    @Mock
    private ControlAuthorizationClient authorizationClient;

    private GrpcFlightControlService service;

    @BeforeEach
    void setUp() {
        service = new GrpcFlightControlService(client, authorizationClient);
    }

    @Test
    void capturesOnlyAfterBackendAuthorizesMissionAndDrone() {
        var context = context(true, true, true);
        when(authorizationClient.authorize("MISSION-1", "DRONE-1", "token"))
                .thenReturn(context);
        when(client.captureImage("command-1", "MISSION-1", "DRONE-1", "token"))
                .thenReturn(Media.MediaCommandAck.newBuilder()
                        .setCommandId("command-1")
                        .setState(Media.CommandState.COMMAND_STATE_SUCCEEDED)
                        .build());

        CommandResponse response = service.captureImage(
                "command-1", "MISSION-1", "DRONE-1", "token");

        assertThat(response.state()).isEqualTo("SUCCEEDED");
        verify(authorizationClient).authorize("MISSION-1", "DRONE-1", "token");
        verify(client).captureImage("command-1", "MISSION-1", "DRONE-1", "token");
    }

    @Test
    void rejectsCaptureWhenMissionStateDoesNotAllowIt() {
        when(authorizationClient.authorize("MISSION-1", "DRONE-1", "token"))
                .thenReturn(context(true, false, false));

        assertThatThrownBy(() -> service.captureImage(
                "command-1", "MISSION-1", "DRONE-1", "token"))
                .isInstanceOf(ControlGatewayException.class)
                .hasMessage("Mission does not allow media capture");

        verifyNoInteractions(client);
    }

    @Test
    void authorizesUploadUsingMediaMissionAndAssignedDrone() {
        Media.LocalMedia media = Media.LocalMedia.newBuilder()
                .setLocalMediaId("media-1")
                .setMissionId("MISSION-1")
                .setDroneId("DRONE-1")
                .setMediaType(Media.MediaType.MEDIA_TYPE_IMAGE)
                .setStatus(Media.LocalMediaStatus.LOCAL_MEDIA_STATUS_REVIEW_PENDING)
                .setFileName("capture.jpg")
                .setContentType("image/jpeg")
                .build();
        when(client.listMedia("", "token")).thenReturn(List.of(media));
        when(authorizationClient.authorize("MISSION-1", "DRONE-1", "token"))
                .thenReturn(context(true, true, true));
        when(client.uploadMedia("command-1", "media-1", "token"))
                .thenReturn(Media.MediaCommandAck.newBuilder()
                        .setCommandId("command-1")
                        .setState(Media.CommandState.COMMAND_STATE_ACCEPTED)
                        .setMedia(media)
                        .build());

        CommandResponse response = service.uploadMedia("command-1", "media-1", "token");

        assertThat(response.state()).isEqualTo("ACCEPTED");
        assertThat(response.media().previewUrl())
                .isEqualTo("/api/control/v1/media/media-1/preview");
        verify(authorizationClient).authorize("MISSION-1", "DRONE-1", "token");
    }

    private ControlAuthorizationContext context(
            boolean controlAllowed,
            boolean captureAllowed,
            boolean uploadAllowed) {
        return new ControlAuthorizationContext(
                "mission-uuid",
                "MISSION-1",
                "DRONE-1",
                "operator-1",
                "IN_PROGRESS",
                controlAllowed,
                captureAllowed,
                uploadAllowed);
    }
}
