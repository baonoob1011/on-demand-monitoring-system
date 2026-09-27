package com.ondemandmonitoring.media.service;

import com.ondemandmonitoring.media.domain.MediaAsset;

public interface IMediaObjectVerificationService {
    /** Returns a validation failure or null when metadata and content match. */
    String verify(MediaAsset asset, String key);
}
