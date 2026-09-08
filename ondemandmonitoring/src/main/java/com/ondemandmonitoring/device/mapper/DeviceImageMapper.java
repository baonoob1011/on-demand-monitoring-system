package com.ondemandmonitoring.device.mapper;

import com.ondemandmonitoring.device.domain.DeviceImage;
import com.ondemandmonitoring.device.dto.response.DeviceImageResponse;
import com.ondemandmonitoring.device.dto.response.MediaResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DeviceImageMapper {

    DeviceImageResponse toResponse(DeviceImage image);

    @Mapping(source = "image.id", target = "id")
    @Mapping(source = "image.deviceCode", target = "droneId")
    @Mapping(source = "presignedUrl", target = "url")
    @Mapping(source = "expiresInSeconds", target = "expiresIn")
    MediaResponse toMediaResponse(DeviceImage image, String presignedUrl, long expiresInSeconds);
}
