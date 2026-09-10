package com.ondemandmonitoring.user.service;

import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.enumeration.IdentityProvider;

import java.util.Optional;
import java.util.UUID;

public interface IUserService {

    User findByEmail(String email);

    User createLocalUser(String email, String fullName, RoleCode role,
                         String cognitoUsername, String cognitoSub);

    User createSocialUser(String email, String fullName, RoleCode role,
                          String cognitoUsername, String cognitoSub);

    User createManagedUser(String email, String fullName, RoleCode role,
                           String cognitoUsername, String cognitoSub);

    Optional<User> findOptionalByEmail(String email);

    String getCognitoUsername(String email, IdentityProvider provider);

    void markEmailVerified(String email);

    void recordLogin(UUID userId);

    User syncSocialIdentity(User user, String cognitoUsername, String cognitoSub,
                            String fullName, boolean emailVerified);

    boolean hasIdentity(UUID userId, IdentityProvider provider);

    User findByCognitoSub(String cognitoSub);

    void linkLocalIdentity(User user, String cognitoUsername, String cognitoSub);

}
