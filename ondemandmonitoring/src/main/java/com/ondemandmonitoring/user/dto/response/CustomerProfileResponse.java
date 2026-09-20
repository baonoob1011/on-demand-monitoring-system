package com.ondemandmonitoring.user.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CustomerProfileResponse {

    private String phoneNumber;

    private String address;

    private String companyName;
}
