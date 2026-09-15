package com.ondemandmonitoring.media.service;

import com.ondemandmonitoring.media.dto.MediaUploadResponse;
import com.ondemandmonitoring.media.dto.PrepareMediaUploadRequest;
import com.ondemandmonitoring.media.dto.ReportUploadFailureRequest;
import com.ondemandmonitoring.media.event.StorageObjectCreatedEvent;

public interface IMediaUploadService {

    MediaUploadResponse prepare(String missionId, PrepareMediaUploadRequest request);

    MediaUploadResponse retry(String mediaId);

    MediaUploadResponse prepareManualUpload(String mediaId);

    MediaUploadResponse reportFailure(
            String mediaId, String attemptId, ReportUploadFailureRequest request);

    void markUploaded(String mediaId, String attemptId);

    MediaUploadResponse getStatus(String mediaId);

    void processObjectCreated(StorageObjectCreatedEvent event);
}
