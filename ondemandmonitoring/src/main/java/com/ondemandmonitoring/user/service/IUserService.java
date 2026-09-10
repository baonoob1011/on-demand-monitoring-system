package com.ondemandmonitoring.user.service;

import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.enumeration.UserRole;

import java.util.Optional;
import java.util.UUID;

public interface IUserService {

    User findByEmail(String email);

    User createLocalUser(String email, String fullName, UserRole role,
                         String cognitoUsername, String cognitoSub);

    User createManagedUser(String email, String fullName, UserRole role,
                           String cognitoUsername, String cognitoSub);

    Optional<User> findOptionalByEmail(String email);

    void markEmailVerified(String email);

    void recordLogin(UUID userId);

    User syncExternalIdentity(User user, String cognitoUsername, String cognitoSub,
                              String fullName, boolean emailVerified);

}
