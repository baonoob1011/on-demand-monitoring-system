package com.ondemandmonitoring.auth.port.out;

/**
 * Schedules recoverable authentication side effects that must outlive the
 * transaction which requested them.
 */
public interface AuthCompensationPort {

    void scheduleCognitoCleanup(String username, String cognitoSub);

    void scheduleAccountStatusSync(String username, boolean active);
}
