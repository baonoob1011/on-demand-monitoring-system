package com.ondemandmonitoring.user.service;

import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.domain.UserIdentity;

import java.util.UUID;

public interface UserIdentityService {

    UserIdentity create(User user, IdentityProvider provider, String cognitoUsername, String cognitoSub);

    UserIdentity link(User user, IdentityProvider provider, String cognitoUsername, String cognitoSub);

    String getUsername(UUID userId, IdentityProvider provider);

    boolean hasIdentity(UUID userId, IdentityProvider provider);

    User findUserByCognitoSub(String cognitoSub);
}
