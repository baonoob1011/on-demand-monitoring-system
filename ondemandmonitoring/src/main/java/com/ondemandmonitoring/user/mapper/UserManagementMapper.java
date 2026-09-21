package com.ondemandmonitoring.user.mapper;

import com.ondemandmonitoring.user.domain.CustomerProfile;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.domain.UserIdentity;
import com.ondemandmonitoring.user.dto.response.CustomerProfileResponse;
import com.ondemandmonitoring.user.dto.response.UserManagementDetailResponse;
import com.ondemandmonitoring.user.dto.response.UserManagementSummaryResponse;
import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import java.util.Comparator;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserManagementMapper {

    @Mapping(source = "role.code", target = "role")
    @Mapping(source = "isActive", target = "active")
    UserManagementSummaryResponse toSummary(User user);

    @Mapping(source = "user.id", target = "id")
    @Mapping(source = "user.fullName", target = "fullName")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "user.role.code", target = "role")
    @Mapping(source = "user.isActive", target = "active")
    @Mapping(source = "user.emailVerified", target = "emailVerified")
    @Mapping(source = "user.createdAt", target = "createdAt")
    @Mapping(source = "user.updatedAt", target = "updatedAt")
    @Mapping(source = "user.lastLoginAt", target = "lastLoginAt")
    @Mapping(source = "identities", target = "linkedProviders")
    @Mapping(source = "customerProfile", target = "customerProfile")
    UserManagementDetailResponse toDetail(
            User user,
            List<UserIdentity> identities,
            CustomerProfile customerProfile);

    CustomerProfileResponse toCustomerProfile(CustomerProfile customerProfile);

    default List<IdentityProvider> toLinkedProviders(List<UserIdentity> identities) {
        return identities.stream()
                .map(UserIdentity::getProvider)
                .distinct()
                .sorted(Comparator.comparing(Enum::name))
                .toList();
    }
}
