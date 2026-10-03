package com.ondemandmonitoring.role.domain;

public enum RoleCode {
    CUSTOMER,
    STAFF,
    MANAGER,
    ADMIN;

    public boolean isEmployeeRole() {
        return this == STAFF || this == MANAGER;
    }
}
