package com.ondemandmonitoring.user.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.domain.UserIdentity;
import com.ondemandmonitoring.user.repository.UserIdentityRepository;
import com.ondemandmonitoring.user.service.UserIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserIdentityServiceImpl implements UserIdentityService {

    private final UserIdentityRepository identityRepository;

    @Override
    @Transactional
    public UserIdentity create(User user, IdentityProvider provider,
                               String cognitoUsername, String cognitoSub) {
        if (identityRepository.existsByCognitoSubAndProvider(cognitoSub, provider)) {
            throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "Cognito identity is already linked to an account");
        }
        return identityRepository.save(UserIdentity.builder()
                .user(user)
                .provider(provider)
                .cognitoUsername(cognitoUsername)
                .cognitoSub(cognitoSub)
                .build());
    }

    @Override
    @Transactional
    public UserIdentity link(User user, IdentityProvider provider,
                             String cognitoUsername, String cognitoSub) {
        return identityRepository.findByUserIdAndProvider(user.getId(), provider)
                .map(existing -> {
                    if (!existing.getCognitoSub().equals(cognitoSub)) {
                        throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                                "An identity for this provider is already linked to this account");
                    }
                    return existing;
                })
                .orElseGet(() -> create(user, provider, cognitoUsername, cognitoSub));
    }

    @Override
    @Transactional(readOnly = true)
    public String getUsername(UUID userId, IdentityProvider provider) {
        return identityRepository.findByUserIdAndProvider(userId, provider)
                .map(UserIdentity::getCognitoUsername)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND,
                        "Authentication identity is not linked to this account"));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasIdentity(UUID userId, IdentityProvider provider) {
        return identityRepository.findByUserIdAndProvider(userId, provider).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserByCognitoSub(String cognitoSub) {
        return identityRepository.findByCognitoSub(cognitoSub)
                .map(UserIdentity::getUser)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }
}
