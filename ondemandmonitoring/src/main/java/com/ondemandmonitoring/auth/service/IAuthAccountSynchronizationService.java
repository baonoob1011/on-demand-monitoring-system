package com.ondemandmonitoring.auth.service;

/**
 * Public auth-module contract used by other modules to synchronize account
 * state with the configured identity provider.
 */
public interface IAuthAccountSynchronizationService {

    void scheduleAccountStatusSync(String username, boolean active);
}
