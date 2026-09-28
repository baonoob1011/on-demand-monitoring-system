package com.ondemandmonitoring.devicecheck.enums;

public enum PreDeviceCheckType {
    GAZEBO("GAZEBO", "Gazebo Simulation", PreDeviceCheckLevel.CRITICAL),
    PX4("PX4", "PX4 Flight Controller", PreDeviceCheckLevel.CRITICAL),
    MAVSDK("MAVSDK", "MAVSDK Connection", PreDeviceCheckLevel.CRITICAL),
    PX4_CONTROL("PX4_CONTROL", "PX4 Control", PreDeviceCheckLevel.CRITICAL),
    LOCAL_POSITION("LOCAL_POSITION", "Local Position", PreDeviceCheckLevel.CRITICAL),
    MAVSDK_HEALTH("MAVSDK_HEALTH", "MAVSDK Health", PreDeviceCheckLevel.CRITICAL),
    BATTERY("BATTERY", "Battery", PreDeviceCheckLevel.CRITICAL),
    WEATHER("WEATHER", "Weather", PreDeviceCheckLevel.WARNING),
    LIDAR("LIDAR", "LiDAR", PreDeviceCheckLevel.WARNING),
    CAMERA("CAMERA", "Downward Camera", PreDeviceCheckLevel.WARNING),
    BACKEND("BACKEND", "Backend Connection", PreDeviceCheckLevel.WARNING),
    MEDIA("MEDIA", "Media Storage Probe", PreDeviceCheckLevel.CRITICAL),
    MODULES("MODULES", "Module Check", PreDeviceCheckLevel.INFO);

    private final String code;
    private final String displayName;
    private final PreDeviceCheckLevel level;

    PreDeviceCheckType(String code, String displayName, PreDeviceCheckLevel level) {
        this.code = code;
        this.displayName = displayName;
        this.level = level;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public PreDeviceCheckLevel level() {
        return level;
    }
}
