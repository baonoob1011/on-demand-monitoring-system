package com.ondemandmonitoring.lambda;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminLinkProviderForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CognitoPreSignUpHandlerTest {

    @Test
    void ignoresNonExternalSignup() {
        CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);
        Map<String, Object> event = event("PreSignUp_SignUp", true);

        new CognitoPreSignUpHandler(cognito).handleRequest(event, null);

        verifyNoInteractions(cognito);
    }

    @Test
    void unverifiedExternalEmailIsNotLinked() {
        CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);

        new CognitoPreSignUpHandler(cognito).handleRequest(event("PreSignUp_ExternalProvider", false), null);

        verifyNoInteractions(cognito);
    }

    @Test
    void linksVerifiedGoogleIdentityToExistingLocalProfile() {
        CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);
        when(cognito.listUsers(any(ListUsersRequest.class))).thenReturn(ListUsersResponse.builder()
                .users(UserType.builder().username("local-uuid")
                        .attributes(AttributeType.builder().name("email").value("user@example.com").build())
                        .build())
                .build());
        Map<String, Object> event = event("PreSignUp_ExternalProvider", true);

        new CognitoPreSignUpHandler(cognito).handleRequest(event, null);

        ArgumentCaptor<AdminLinkProviderForUserRequest> request =
                ArgumentCaptor.forClass(AdminLinkProviderForUserRequest.class);
        verify(cognito).adminLinkProviderForUser(request.capture());
        assertEquals("local-uuid", request.getValue().destinationUser().providerAttributeValue());
        assertEquals("116001", request.getValue().sourceUser().providerAttributeValue());
        Map<?, ?> response = (Map<?, ?>) event.get("response");
        assertEquals(true, response.get("autoConfirmUser"));
        assertEquals(true, response.get("autoVerifyEmail"));
    }

    @Test
    void doesNotLinkWhenOnlyFederatedProfileExists() {
        CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);
        when(cognito.listUsers(any(ListUsersRequest.class))).thenReturn(ListUsersResponse.builder()
                .users(UserType.builder().username("google_116001")
                        .attributes(AttributeType.builder().name("identities").value("[]").build())
                        .build())
                .build());

        new CognitoPreSignUpHandler(cognito).handleRequest(event("PreSignUp_ExternalProvider", true), null);

        verify(cognito, never()).adminLinkProviderForUser(any(AdminLinkProviderForUserRequest.class));
    }

    private Map<String, Object> event(String triggerSource, boolean verified) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("email", " User@Example.com ");
        attributes.put("email_verified", Boolean.toString(verified));
        attributes.put("identities", "[{\"userId\":\"116001\",\"providerName\":\"Google\"}]");

        Map<String, Object> event = new HashMap<>();
        event.put("triggerSource", triggerSource);
        event.put("userPoolId", "ap-southeast-1_pool");
        event.put("userName", "google_116001");
        event.put("request", new HashMap<>(Map.of("userAttributes", attributes)));
        event.put("response", new HashMap<>());
        return event;
    }
}
