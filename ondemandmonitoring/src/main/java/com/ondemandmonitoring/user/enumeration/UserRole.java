package com.ondemandmonitoring.user.enumeration;

public enum UserRole {
    CUSTOMER,
    STAFF,
    DRONE_OPERATOR,
    SYSTEM_OPERATOR,
    ADMIN;

    public boolean isEmployeeRole() {
        return this == STAFF || this == DRONE_OPERATOR || this == SYSTEM_OPERATOR;
    }
}
