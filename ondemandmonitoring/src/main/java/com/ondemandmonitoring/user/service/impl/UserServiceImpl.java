package com.ondemandmonitoring.user.service.impl;

import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.enumeration.UserRole;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.user.service.IUserService;
import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final UserRepository userRepository;

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional
    public User createLocalUser(String email, String fullName, UserRole role,
                                String cognitoUsername, String cognitoSub) {
        return createUser(email, fullName, role, cognitoUsername, cognitoSub, false);
    }

    @Override
    @Transactional
    public User createManagedUser(String email, String fullName, UserRole role,
                                  String cognitoUsername, String cognitoSub) {
        return createUser(email, fullName, role, cognitoUsername, cognitoSub, true);
    }

    private User createUser(String email, String fullName, UserRole role,
                            String cognitoUsername, String cognitoSub, boolean emailVerified) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        return userRepository.save(User.builder()
                .email(normalizedEmail)
                .fullName(fullName.trim())
                .role(role)
                .cognitoUsername(cognitoUsername)
                .cognitoSub(cognitoSub)
                .emailVerified(emailVerified)
                .isActive(true)
                .build());
    }

    @Override
    public Optional<User> findOptionalByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email));
    }

    @Override
    @Transactional
    public void markEmailVerified(String email) {
        User user = findByEmail(email);
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void recordLogin(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    @Override
    @Transactional
    public User syncExternalIdentity(User user, String cognitoUsername, String cognitoSub,
                                     String fullName, boolean emailVerified) {
        userRepository.findByCognitoSub(cognitoSub)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                            "Cognito identity is already linked to another account");
                });
        user.setCognitoUsername(cognitoUsername);
        user.setCognitoSub(cognitoSub);
        user.setEmailVerified(Boolean.TRUE.equals(user.getEmailVerified()) || emailVerified);
        if ((user.getFullName() == null || user.getFullName().isBlank())
                && fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }
        return userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
