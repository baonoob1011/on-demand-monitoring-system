package com.ondemandmonitoring.drone.controller;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import com.ondemandmonitoring.drone.dto.request.TelemetryRequest;
import com.ondemandmonitoring.drone.service.impl.DroneTelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InternalDroneTelemetryControllerTest {

    private DroneTelemetryService service;
    private InternalDroneTelemetryController controller;

    @BeforeEach
    void setUp() {
        service = mock(DroneTelemetryService.class);
        controller = new InternalDroneTelemetryController(service);
        ReflectionTestUtils.setField(controller, "configuredSecret", "local-test-secret");
    }

    @Test
    void rejectsMissingOrWrongSecretBeforeSaving() {
        TelemetryRequest request = new TelemetryRequest();

        assertThatThrownBy(() -> controller.receive("DRN-0048", null, request))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.receive("DRN-0048", "wrong", request))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(service);
    }

    @Test
    void savesAuthenticatedTelemetryForRequestedDrone() {
        TelemetryRequest request = new TelemetryRequest();
        DroneTelemetry telemetry = new DroneTelemetry();
        when(service.saveForRegisteredDrone("DRN-0048", request)).thenReturn(telemetry);

        var response = controller.receive("DRN-0048", "local-test-secret", request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        verify(service).saveForRegisteredDrone("DRN-0048", request);
    }
}
