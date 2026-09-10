package com.ondemandmonitoring.user.service.impl;

import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.enumeration.IdentityProvider;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.role.domain.Role;
import com.ondemandmonitoring.role.service.RoleService;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.user.service.IUserService;
import com.ondemandmonitoring.user.service.UserIdentityService;
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
    private final RoleService roleService;
    private final UserIdentityService userIdentityService;

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional
    public User createLocalUser(String email, String fullName, RoleCode role,
                                String cognitoUsername, String cognitoSub) {
        return createUser(email, fullName, role, IdentityProvider.LOCAL,
                cognitoUsername, cognitoSub, false);
    }

    @Override
    @Transactional
    public User createSocialUser(String email, String fullName, RoleCode role,
                                 String cognitoUsername, String cognitoSub) {
        return createUser(email, fullName, role, IdentityProvider.GOOGLE,
                cognitoUsername, cognitoSub, true);
    }

    @Override
    @Transactional
    public User createManagedUser(String email, String fullName, RoleCode role,
                                  String cognitoUsername, String cognitoSub) {
        return createUser(email, fullName, role, IdentityProvider.LOCAL,
                cognitoUsername, cognitoSub, true);
    }

    private User createUser(String email, String fullName, RoleCode role,
                            IdentityProvider provider, String cognitoUsername,
                            String cognitoSub, boolean emailVerified) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Role roleEntity = roleService.getActiveRole(role);
        User user = userRepository.save(User.builder()
                .email(normalizedEmail)
                .fullName(fullName.trim())
                .role(roleEntity)
                .emailVerified(emailVerified)
                .isActive(true)
                .build());
        userIdentityService.create(user, provider, cognitoUsername, cognitoSub);
        return user;
    }

    @Override
    public Optional<User> findOptionalByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email));
    }

    @Override
    @Transactional(readOnly = true)
    public String getCognitoUsername(String email, IdentityProvider provider) {
        User user = findByEmail(email);
        return userIdentityService.getUsername(user.getId(), provider);
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
    public User syncSocialIdentity(User user, String cognitoUsername, String cognitoSub,
                                   String fullName, boolean emailVerified) {
        userIdentityService.link(user, IdentityProvider.GOOGLE, cognitoUsername, cognitoSub);
        user.setEmailVerified(Boolean.TRUE.equals(user.getEmailVerified()) || emailVerified);
        if ((user.getFullName() == null || user.getFullName().isBlank())
                && fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }
        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasIdentity(UUID userId, IdentityProvider provider) {
        return userIdentityService.hasIdentity(userId, provider);
    }

    @Override
    @Transactional(readOnly = true)
    public User findByCognitoSub(String cognitoSub) {
        return userIdentityService.findUserByCognitoSub(cognitoSub);
    }

    @Override
    @Transactional
    public void linkLocalIdentity(User user, String cognitoUsername, String cognitoSub) {
        userIdentityService.link(user, IdentityProvider.LOCAL, cognitoUsername, cognitoSub);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
