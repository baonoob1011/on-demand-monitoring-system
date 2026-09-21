package com.ondemandmonitoring.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateRequest {

    @Size(max = 100, message = "Full name must not exceed 100 characters")
    @Pattern(regexp = ".*\\S.*", message = "Full name must not be blank")
    private String fullName;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    @Pattern(
            regexp = "^$|^[0-9+() .-]{8,20}$",
            message = "Phone number format is invalid")
    private String phoneNumber;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    @Size(max = 200, message = "Company name must not exceed 200 characters")
    private String companyName;
}
