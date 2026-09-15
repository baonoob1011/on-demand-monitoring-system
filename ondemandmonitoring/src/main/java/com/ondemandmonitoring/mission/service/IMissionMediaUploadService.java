package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.media.domain.MediaAsset;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for handling mission media uploads with automatic retry logic (Flow 3.4).
 */
public interface IMissionMediaUploadService {

    /**
     * Upload media for a mission with automatic retry (up to 3 attempts).
     *
     * @param missionId  Target mission ID
     * @param deviceCode Drone device code
     * @param file       Multipart media file (image / video)
     * @return Saved MediaAsset record
     */
    MediaAsset uploadWithRetry(String missionId, String deviceCode, MultipartFile file);

    MediaAsset uploadWithRetry(String missionId, String deviceCode, MultipartFile file, String mediaType);
}
