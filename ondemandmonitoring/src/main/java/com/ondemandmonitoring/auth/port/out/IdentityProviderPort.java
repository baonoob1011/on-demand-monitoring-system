package com.ondemandmonitoring.auth.port.out;

import com.ondemandmonitoring.auth.dto.request.RegisterRequest;
import com.ondemandmonitoring.auth.dto.request.CreateManagedAccountRequest;

/** Outbound port for external identity providers such as Amazon Cognito. */
public interface IdentityProviderPort {

    String signUp(RegisterRequest request);

    ManagedIdentity createInvitedUser(CreateManagedAccountRequest request);

    void confirmSignUp(String username, String otpCode);

    void resendConfirmationCode(String username);

    AuthenticationTokens authenticate(String username, String password);

    AuthenticationTokens respondToNewPasswordChallenge(String username, String session, String newPassword);

    AuthenticationTokens refresh(String refreshToken, String username);

    void forgotPassword(String username);

    void resetPassword(String username, String otpCode, String newPassword);

    void revoke(String refreshToken);

    void globalSignOut(String accessToken);

    void addUserToGroup(String username, String role);

    void setPermanentPassword(String username, String password);

    void deleteUser(String username);

}
