package com.ondemandmonitoring.mission.mapper;

import com.ondemandmonitoring.media.domain.MediaAsset;
import com.ondemandmonitoring.media.dto.response.MediaResponse;
import com.ondemandmonitoring.media.mapper.MediaAssetMapper;
import com.ondemandmonitoring.media.service.IMediaAssetService;
import com.ondemandmonitoring.mission.domain.MissionResult;
import com.ondemandmonitoring.mission.dto.response.MissionResultResponse;
import java.util.List;
import org.mapstruct.Builder;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public abstract class MissionResultMapper {

    @Autowired
    protected MediaAssetMapper mediaAssetMapper;

    @Mapping(target = "missionId", source = "mission.id")
    @Mapping(target = "missionCode", source = "mission.missionCode")
    @Mapping(target = "mediaFiles", expression = "java(toMediaResponses(result.getMediaFiles(), mediaAssetService))")
    public abstract MissionResultResponse toResponse(
            MissionResult result,
            @Context IMediaAssetService mediaAssetService);

    protected List<MediaResponse> toMediaResponses(
            List<MediaAsset> mediaFiles,
            @Context IMediaAssetService mediaAssetService) {
        if (mediaFiles == null) {
            return List.of();
        }
        return mediaFiles.stream()
                .map(media -> mediaAssetMapper.toMediaResponse(
                        media,
                        mediaAssetService.createPresignedGetUrl(media),
                        mediaAssetService.presignedUrlExpiresSeconds()))
                .toList();
    }
}
