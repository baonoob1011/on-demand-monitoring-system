package com.ondemandmonitoring.role.domain;

public enum RoleCode {
    CUSTOMER,
    DRONE_OPERATOR,
    SYSTEM_OPERATOR,
    ADMIN;

    public boolean isEmployeeRole() {
        return this == DRONE_OPERATOR || this == SYSTEM_OPERATOR;
    }
}
