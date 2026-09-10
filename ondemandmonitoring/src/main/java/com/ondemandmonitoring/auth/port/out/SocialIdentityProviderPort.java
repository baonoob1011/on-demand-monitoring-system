package com.ondemandmonitoring.auth.port.out;

import com.ondemandmonitoring.auth.dto.request.SocialSyncRequest;

public interface SocialIdentityProviderPort {

    SocialAuthenticationResult exchangeSocialCode(SocialSyncRequest request);
}
