package com.ondemandmonitoring.controlgateway.client;

import monitoring.flightcontroller.v1.Media;

import java.util.Iterator;

public record PreviewStream(
        Media.MediaPreviewChunk firstChunk,
        Iterator<Media.MediaPreviewChunk> remainingChunks,
        long requestedOffset,
        long contentLength
) {
    public String contentType() {
        return firstChunk.getContentType();
    }

    public long totalSize() {
        return firstChunk.getTotalSize();
    }
}
