package com.ondemandmonitoring.role.domain;

public enum RoleCode {
    CUSTOMER,
    STAFF,
    DRONE_OPERATOR,
    SYSTEM_OPERATOR,
    ADMIN;

    public boolean isEmployeeRole() {
        return this == STAFF || this == DRONE_OPERATOR || this == SYSTEM_OPERATOR;
    }
}
