package com.ondemandmonitoring.auth.service.impl;

import com.ondemandmonitoring.auth.port.out.AuthCompensationPort;
import com.ondemandmonitoring.auth.service.IAuthAccountSynchronizationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthAccountSynchronizationServiceImpl
        implements IAuthAccountSynchronizationService {

    AuthCompensationPort compensationPort;

    @Override
    public void scheduleAccountStatusSync(String username, boolean active) {
        compensationPort.scheduleAccountStatusSync(username, active);
    }
}
