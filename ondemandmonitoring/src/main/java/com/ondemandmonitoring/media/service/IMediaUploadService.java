package com.ondemandmonitoring.media.service;

import com.ondemandmonitoring.media.dto.*;

public interface IMediaUploadService {
    MediaUploadResponse prepare(String missionId, PrepareMediaUploadRequest request);
    MediaUploadResponse retry(String mediaId, boolean manual);
    MediaUploadResponse reportFailure(String mediaId, String attemptId, ReportUploadFailureRequest request);
    MediaUploadResponse status(String mediaId);
    MediaUploadResponse presignPart(String mediaId, String attemptId, int partNumber);
    void completeMultipart(String mediaId, String attemptId, CompleteMultipartRequest request);
    void markUploaded(String mediaId, String attemptId);
}
