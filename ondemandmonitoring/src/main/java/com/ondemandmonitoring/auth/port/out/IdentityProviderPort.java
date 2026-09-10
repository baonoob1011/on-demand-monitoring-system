package com.ondemandmonitoring.auth.port.out;

import com.ondemandmonitoring.auth.dto.request.RegisterRequest;
import com.ondemandmonitoring.auth.dto.request.CreateManagedAccountRequest;

/** Outbound port for external identity providers such as Amazon Cognito. */
public interface IdentityProviderPort {

    String signUp(RegisterRequest request);

    ManagedIdentity createInvitedUser(CreateManagedAccountRequest request);

    void confirmSignUp(String email, String otpCode);

    void resendConfirmationCode(String email);

    AuthenticationTokens authenticate(String email, String password);

    AuthenticationTokens respondToNewPasswordChallenge(String email, String session, String newPassword);

    AuthenticationTokens refresh(String refreshToken, String username);

    void forgotPassword(String email);

    void resetPassword(String email, String otpCode, String newPassword);

    void revoke(String refreshToken);

    void globalSignOut(String accessToken);

    void addUserToGroup(String username, String role);

    void deleteUser(String username);

}
