package com.ondemandmonitoring.auth.dto.response;

import com.ondemandmonitoring.user.enumeration.UserRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ManagedAccountResponse {

    private String email;
    private UserRole role;
    private boolean invitationSent;
    private boolean passwordChangeRequired;
}
