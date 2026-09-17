package com.ondemandmonitoring.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminLinkProviderForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ProviderUserIdentifierType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cognito Pre Sign-up trigger for external providers.
 *
 * <p>If a verified Google email already belongs to a local Cognito profile,
 * the provider subject is linked before Cognito creates another profile.</p>
 */
public final class CognitoPreSignUpHandler
        implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final Logger LOGGER = Logger.getLogger(CognitoPreSignUpHandler.class.getName());
    private static final String GOOGLE_PROVIDER = "Google";
    private static final Pattern USER_ID_PATTERN = Pattern.compile(
            "\\\"userId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern PROVIDER_NAME_PATTERN = Pattern.compile(
            "\\\"providerName\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final CognitoIdentityProviderClient cognito;

    public CognitoPreSignUpHandler() {
        this.cognito = CognitoIdentityProviderClient.create();
    }

    // Visible for unit tests and local verification.
    CognitoPreSignUpHandler(CognitoIdentityProviderClient cognito) {
        this.cognito = cognito;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        System.out.println("PRE_SIGNUP_INVOKED triggerSource=" + event.get("triggerSource")
                + " userPoolId=" + event.get("userPoolId"));
        if (!"PreSignUp_ExternalProvider".equals(event.get("triggerSource"))) {
            return event;
        }

        Map<String, Object> request = map(event.get("request"));
        Map<String, Object> attributes = map(request.get("userAttributes"));
        String email = normalize(string(attributes.get("email")));
        if (email == null || !isTrue(attributes.get("email_verified"))) {
            return event;
        }

        String identities = string(attributes.get("identities"));
        String providerName = firstMatch(PROVIDER_NAME_PATTERN, identities);
        String providerSubject = firstMatch(USER_ID_PATTERN, identities);
        if (providerSubject == null) {
            String externalUsername = string(event.get("userName"));
            String[] fallback = parseGoogleUsername(externalUsername);
            if (fallback != null) {
                providerName = fallback[0];
                providerSubject = fallback[1];
            }
        }
        System.out.println("EXTERNAL_SIGNUP email=" + email
                + " provider=" + providerName + " hasProviderSubject=" + (providerSubject != null));
        if (!GOOGLE_PROVIDER.equals(providerName) || providerSubject == null) {
            System.out.println("SKIP_LINK missing_or_unsupported_provider_metadata");
            return event;
        }

        String poolId = string(event.get("userPoolId"));
        String localUsername = findLocalUsername(poolId, email);
        if (localUsername == null) {
            System.out.println("NO_LOCAL_PROFILE Cognito_will_create_social_profile");
            return event;
        }

        System.out.println("LOCAL_PROFILE_FOUND destinationUsername=" + localUsername);

        linkGoogleIdentity(poolId, localUsername, providerSubject);
        Map<String, Object> response = mutableMap(event, "response");
        response.put("autoConfirmUser", true);
        response.put("autoVerifyEmail", true);
        return event;
    }

    private String findLocalUsername(String poolId, String email) {
        ListUsersRequest request = ListUsersRequest.builder()
                .userPoolId(poolId)
                .filter("email = \"" + escapeFilterValue(email) + "\"")
                .limit(10)
                .build();

        return cognito.listUsers(request).users().stream()
                .filter(this::isLocalProfile)
                .map(UserType::username)
                .findFirst()
                .orElse(null);
    }

    private boolean isLocalProfile(UserType user) {
        return user.attributes().stream().noneMatch(attribute -> "identities".equals(attribute.name()));
    }

    private void linkGoogleIdentity(String poolId, String localUsername, String providerSubject) {
        try {
            cognito.adminLinkProviderForUser(AdminLinkProviderForUserRequest.builder()
                    .userPoolId(poolId)
                    .destinationUser(ProviderUserIdentifierType.builder()
                            .providerName("Cognito")
                            .providerAttributeName("Cognito_Subject")
                            .providerAttributeValue(localUsername)
                            .build())
                    .sourceUser(ProviderUserIdentifierType.builder()
                            .providerName(GOOGLE_PROVIDER)
                            .providerAttributeName("Cognito_Subject")
                            .providerAttributeValue(providerSubject)
                            .build())
                    .build());
        } catch (CognitoIdentityProviderException exception) {
            String code = exception.awsErrorDetails() == null
                    ? null : exception.awsErrorDetails().errorCode();
            if (!"AliasExistsException".equals(code)
                    && !"ResourceConflictException".equals(code)) {
                LOGGER.log(Level.SEVERE, "Unable to link Google identity", exception);
                throw exception;
            }
            LOGGER.info("Google identity is already linked or is being linked concurrently");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mutableMap(Map<String, Object> event, String key) {
        Object value = event.get(key);
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        Map<String, Object> created = new HashMap<>();
        event.put(key, created);
        return created;
    }

    private static String firstMatch(Pattern pattern, String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    private static boolean isTrue(Object value) {
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static String escapeFilterValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String[] parseGoogleUsername(String username) {
        if (username == null) {
            return null;
        }
        int separator = username.indexOf('_');
        if (separator <= 0 || separator == username.length() - 1
                || !"google".equalsIgnoreCase(username.substring(0, separator))) {
            return null;
        }
        return new String[]{GOOGLE_PROVIDER, username.substring(separator + 1)};
    }
}
