package com.ondemandmonitoring.auth.service;

import com.ondemandmonitoring.auth.dto.request.CreateManagedAccountRequest;
import com.ondemandmonitoring.auth.dto.response.ManagedAccountResponse;

public interface IAdminAccountService {

    ManagedAccountResponse create(CreateManagedAccountRequest request);
}
