package com.ondemandmonitoring.controlgateway.client;

import com.ondemandmonitoring.controlgateway.config.ControlGatewayProperties;
import com.ondemandmonitoring.controlgateway.exception.ControlGatewayException;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import monitoring.flightcontroller.v1.Media;
import monitoring.flightcontroller.v1.MediaServiceGrpc;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class GrpcFlightControllerClient implements FlightControllerClient {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> OPERATOR_AUTHORIZATION =
            Metadata.Key.of("x-operator-authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final Channel channel;
    private final ControlGatewayProperties properties;

    public GrpcFlightControllerClient(Channel channel, ControlGatewayProperties properties) {
        this.channel = channel;
        this.properties = properties;
    }

    @Override
    public Media.FlightControllerHealth health(String operatorAccessToken) {
        return invoke(() -> stub(operatorAccessToken).getHealth(Media.GetHealthRequest.getDefaultInstance()));
    }

    @Override
    public Media.MediaCommandAck captureImage(String commandId, String missionId, String droneId,
                                              String operatorAccessToken) {
        Media.CaptureImageRequest request = Media.CaptureImageRequest.newBuilder()
                .setCommandId(commandId).setMissionId(missionId).setDroneId(droneId).build();
        return invoke(() -> stub(operatorAccessToken).captureImage(request));
    }

    @Override
    public Media.MediaCommandAck startVideo(String commandId, String missionId, String droneId,
                                           String operatorAccessToken) {
        Media.StartVideoRequest request = Media.StartVideoRequest.newBuilder()
                .setCommandId(commandId).setMissionId(missionId).setDroneId(droneId).build();
        return invoke(() -> stub(operatorAccessToken).startVideo(request));
    }

    @Override
    public Media.MediaCommandAck stopVideo(String commandId, String operatorAccessToken) {
        return invoke(() -> stub(operatorAccessToken).stopVideo(
                Media.StopVideoRequest.newBuilder().setCommandId(commandId).build()));
    }

    @Override
    public List<Media.LocalMedia> listMedia(String missionId, String operatorAccessToken) {
        return invoke(() -> stub(operatorAccessToken).listMedia(
                Media.ListMediaRequest.newBuilder().setMissionId(missionId).build()).getMediaList());
    }

    @Override
    public Media.MediaCommandAck discardMedia(String commandId, String localMediaId,
                                              String operatorAccessToken) {
        return invoke(() -> stub(operatorAccessToken).discardMedia(
                Media.DiscardMediaRequest.newBuilder()
                        .setCommandId(commandId).setLocalMediaId(localMediaId).build()));
    }

    @Override
    public Media.MediaCommandAck uploadMedia(String commandId, String localMediaId,
                                             String operatorAccessToken) {
        return invoke(() -> stub(operatorAccessToken).uploadMedia(
                Media.UploadMediaRequest.newBuilder()
                        .setCommandId(commandId).setLocalMediaId(localMediaId).build()));
    }

    @Override
    public Iterator<Media.MediaCommandUpdate> watchCommand(String commandId,
                                                           String operatorAccessToken) {
        try {
            return streamStub(operatorAccessToken).watchCommand(
                    Media.WatchCommandRequest.newBuilder().setCommandId(commandId).build());
        } catch (StatusRuntimeException exception) {
            throw ControlGatewayException.fromGrpc(exception);
        }
    }

    @Override
    public PreviewStream openPreview(String localMediaId, long offset, long length,
                                     String operatorAccessToken) {
        try {
            Iterator<Media.MediaPreviewChunk> chunks = streamStub(operatorAccessToken).getMediaPreview(
                    Media.GetMediaPreviewRequest.newBuilder()
                            .setLocalMediaId(localMediaId).setOffset(offset).setLength(length).build());
            if (!chunks.hasNext()) {
                throw new ControlGatewayException("MEDIA_PREVIEW_EMPTY", "Media preview is empty", 404);
            }
            Media.MediaPreviewChunk first = chunks.next();
            long available = Math.max(0, first.getTotalSize() - offset);
            long contentLength = length <= 0 ? available : Math.min(length, available);
            return new PreviewStream(first, chunks, offset, contentLength);
        } catch (StatusRuntimeException exception) {
            throw ControlGatewayException.fromGrpc(exception);
        }
    }

    private MediaServiceGrpc.MediaServiceBlockingStub stub(String operatorAccessToken) {
        return decorate(MediaServiceGrpc.newBlockingStub(channel), operatorAccessToken)
                .withDeadlineAfter(properties.commandTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private MediaServiceGrpc.MediaServiceBlockingStub streamStub(String operatorAccessToken) {
        return decorate(MediaServiceGrpc.newBlockingStub(channel), operatorAccessToken)
                .withDeadlineAfter(properties.streamTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private MediaServiceGrpc.MediaServiceBlockingStub decorate(
            MediaServiceGrpc.MediaServiceBlockingStub source, String operatorAccessToken) {
        Metadata metadata = new Metadata();
        String serviceToken = properties.flightController().token();
        if (!serviceToken.isBlank()) {
            metadata.put(AUTHORIZATION, "Bearer " + serviceToken);
        }
        if (operatorAccessToken != null && !operatorAccessToken.isBlank()) {
            metadata.put(OPERATOR_AUTHORIZATION, "Bearer " + operatorAccessToken);
        }
        return source.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }

    private <T> T invoke(GrpcSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (StatusRuntimeException exception) {
            throw ControlGatewayException.fromGrpc(exception);
        }
    }

    @FunctionalInterface
    private interface GrpcSupplier<T> {
        T get();
    }
}
