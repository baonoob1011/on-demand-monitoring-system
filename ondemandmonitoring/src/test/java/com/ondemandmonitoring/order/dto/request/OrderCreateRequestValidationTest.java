package com.ondemandmonitoring.order.dto.request;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderCreateRequestValidationTest {

    @Test
    void validatesNestedDeliverableFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = OrderCreateRequest.builder()
                    .deliverables(List.of(OrderDeliverableRequest.builder().build()))
                    .build();
            var violations = factory.getValidator().validate(request);
            var paths = violations.stream().map(v -> v.getPropertyPath().toString()).toList();
            assertTrue(paths.contains("deliverables[0].deliverableTypeId"));
            assertTrue(paths.contains("deliverables[0].requirement"));
        }
    }

    @Test
    void acceptsValidNestedDeliverable() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = OrderCreateRequest.builder()
                    .deliverables(List.of(OrderDeliverableRequest.builder()
                            .deliverableTypeId("type-id").requirement(Map.of()).build()))
                    .build();
            var violations = factory.getValidator().validate(request);
            assertTrue(violations.stream().noneMatch(v ->
                    v.getPropertyPath().toString().startsWith("deliverables")));
        }
    }
}
