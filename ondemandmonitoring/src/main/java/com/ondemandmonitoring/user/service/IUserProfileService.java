package com.ondemandmonitoring.user.service;

import com.ondemandmonitoring.user.dto.request.UserProfileUpdateRequest;
import com.ondemandmonitoring.user.dto.response.UserProfileResponse;

public interface IUserProfileService {

    UserProfileResponse getCurrentProfile();

    UserProfileResponse updateCurrentProfile(UserProfileUpdateRequest request);
}
