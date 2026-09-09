package com.ondemandmonitoring.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class ResendOtpRequest {

    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be a valid email address.")
    private String email;

}
