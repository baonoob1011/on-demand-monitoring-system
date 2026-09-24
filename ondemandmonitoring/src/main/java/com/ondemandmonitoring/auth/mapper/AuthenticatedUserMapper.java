package com.ondemandmonitoring.auth.mapper;

import com.ondemandmonitoring.auth.dto.response.AuthenticatedUserResponse;
import com.ondemandmonitoring.user.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthenticatedUserMapper {

    @Mapping(source = "role.code", target = "role")
    AuthenticatedUserResponse toResponse(User user);
}
