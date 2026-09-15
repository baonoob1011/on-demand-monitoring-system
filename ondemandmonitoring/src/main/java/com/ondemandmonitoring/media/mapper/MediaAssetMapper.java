package com.ondemandmonitoring.media.mapper;

import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.dto.response.MediaAssetResponse;
import com.ondemandmonitoring.media.dto.response.MediaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MediaAssetMapper {

    MediaAssetResponse toResponse(MediaAsset mediaAsset);

    @Mapping(source = "mediaAsset.id", target = "id")
    @Mapping(source = "mediaAsset.deviceCode", target = "droneId")
    @Mapping(source = "presignedUrl", target = "url")
    @Mapping(source = "expiresInSeconds", target = "expiresIn")
    MediaResponse toMediaResponse(MediaAsset mediaAsset, String presignedUrl, long expiresInSeconds);
}
