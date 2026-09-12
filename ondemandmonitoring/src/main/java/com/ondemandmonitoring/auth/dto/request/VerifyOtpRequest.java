package com.ondemandmonitoring.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpRequest {

    @NotBlank(message = "Email is required.")
    @Email(message = "Email must be a valid email address.")
    private String email;

    @NotBlank(message = "OTP code is required.")
    @Pattern(regexp = "^\\d{6}$", message = "OTP code must contain exactly 6 digits.")
    private String otpCode;

}
