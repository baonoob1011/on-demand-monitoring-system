package com.ondemandmonitoring.controlgateway;

import com.ondemandmonitoring.controlgateway.config.ControlGatewayProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ControlGatewayPropertiesTest {

    @Test
    void suppliesSafeLocalDefaults() {
        ControlGatewayProperties properties = new ControlGatewayProperties(null, null, null);

        assertThat(properties.flightController().host()).isEqualTo("127.0.0.1");
        assertThat(properties.flightController().port()).isEqualTo(50051);
        assertThat(properties.commandTimeout()).hasSeconds(15);
    }
}
