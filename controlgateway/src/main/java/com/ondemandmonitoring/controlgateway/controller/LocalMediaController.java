package com.ondemandmonitoring.controlgateway.controller;

import com.ondemandmonitoring.controlgateway.client.PreviewStream;
import com.ondemandmonitoring.controlgateway.exception.ControlGatewayException;
import com.ondemandmonitoring.controlgateway.service.FlightControlService;
import monitoring.flightcontroller.v1.Media;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/control/v1/media")
public class LocalMediaController {

    private static final Pattern RANGE = Pattern.compile("bytes=(\\d+)-(\\d*)");

    private final FlightControlService flightControlService;

    public LocalMediaController(FlightControlService flightControlService) {
        this.flightControlService = flightControlService;
    }

    @GetMapping("/{localMediaId}/preview")
    ResponseEntity<StreamingResponseBody> preview(
            @PathVariable String localMediaId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        RequestedRange requested = parseRange(range);
        PreviewStream preview = flightControlService.openPreview(
                localMediaId, requested.offset(), requested.length(),
                FlightControlController.bearer(authorization));

        long lastByte = preview.requestedOffset() + preview.contentLength() - 1;
        StreamingResponseBody body = output -> {
            output.write(preview.firstChunk().getData().toByteArray());
            Iterator<Media.MediaPreviewChunk> chunks = preview.remainingChunks();
            while (chunks.hasNext()) {
                output.write(chunks.next().getData().toByteArray());
            }
        };

        ResponseEntity.BodyBuilder response = ResponseEntity
                .status(range == null ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(preview.contentType()))
                .contentLength(preview.contentLength())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .cacheControl(CacheControl.noStore());
        if (range != null) {
            response.header(HttpHeaders.CONTENT_RANGE,
                    "bytes " + preview.requestedOffset() + "-" + lastByte + "/" + preview.totalSize());
        }
        return response.body(body);
    }

    private RequestedRange parseRange(String value) {
        if (value == null || value.isBlank()) {
            return new RequestedRange(0, 0);
        }
        Matcher matcher = RANGE.matcher(value.trim());
        if (!matcher.matches()) {
            throw new ControlGatewayException("INVALID_RANGE", "Only a single byte range is supported", 416);
        }
        long start = Long.parseLong(matcher.group(1));
        long length = matcher.group(2).isBlank()
                ? 0
                : Long.parseLong(matcher.group(2)) - start + 1;
        if (length < 0) {
            throw new ControlGatewayException("INVALID_RANGE", "Invalid media byte range", 416);
        }
        return new RequestedRange(start, length);
    }

    private record RequestedRange(long offset, long length) {
    }
}
