package com.ondemandmonitoring.auth.dto.request;

import com.ondemandmonitoring.user.enumeration.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateManagedAccountRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(max = 100)
    private String fullName;

    @NotNull
    private UserRole role;
}
