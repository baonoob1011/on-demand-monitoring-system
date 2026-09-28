package com.ondemandmonitoring.devicecheck.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PreDeviceCheckTypeTest {

    @Test
    void mediaStorageProbeIsFlightCritical() {
        assertThat(PreDeviceCheckType.MEDIA.displayName()).isEqualTo("Media Storage Probe");
        assertThat(PreDeviceCheckType.MEDIA.level()).isEqualTo(PreDeviceCheckLevel.CRITICAL);
    }
}
