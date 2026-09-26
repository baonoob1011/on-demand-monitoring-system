package com.ondemandmonitoring.media.service;

import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.dto.response.MediaUploadResponse;

/** Internal plans; caller must authorize and lock the media aggregate in its transaction. */
public interface IMediaUploadPlanService {
    MediaUploadResponse create(MediaAsset asset, boolean manual);
    MediaUploadResponse resume(MediaAsset asset);
}
