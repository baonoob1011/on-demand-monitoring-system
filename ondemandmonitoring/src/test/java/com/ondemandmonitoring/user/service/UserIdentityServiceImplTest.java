package com.ondemandmonitoring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.domain.UserIdentity;
import com.ondemandmonitoring.user.repository.UserIdentityRepository;
import com.ondemandmonitoring.user.service.impl.UserIdentityServiceImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserIdentityServiceImplTest {

    @Mock
    private UserIdentityRepository identityRepository;

    @InjectMocks
    private UserIdentityServiceImpl identityService;

    @Test
    void findUserByCognitoUsername_returnsLinkedUser() {
        User expected = User.builder().build();
        expected.setId(UUID.randomUUID().toString());
        when(identityRepository.findByCognitoUsername("cognito-user"))
                .thenReturn(Optional.of(UserIdentity.builder().user(expected).build()));

        User actual = identityService.findUserByCognitoUsername("cognito-user");

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void findUserByCognitoUsername_whenMissing_throwsUserNotFound() {
        when(identityRepository.findByCognitoUsername("missing-user"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> identityService.findUserByCognitoUsername("missing-user"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
