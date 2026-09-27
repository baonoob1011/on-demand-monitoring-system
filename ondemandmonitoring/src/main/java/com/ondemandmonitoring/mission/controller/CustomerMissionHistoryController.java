package com.ondemandmonitoring.mission.controller;

import com.ondemandmonitoring.common.api.ApiResponse;
import com.ondemandmonitoring.common.api.PageResponse;
import com.ondemandmonitoring.mission.dto.response.CustomerMissionHistoryResponse;
import com.ondemandmonitoring.mission.service.ICustomerMissionHistoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Customer Mission History", description = "Customer-owned mission history and results")
@RequestMapping("/api/customer/mission-history")
@PreAuthorize("hasRole('CUSTOMER')")
@Validated
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomerMissionHistoryController {
    ICustomerMissionHistoryService historyService;

    @GetMapping
    @Operation(summary = "List customer's completed, failed and cancelled missions")
    public ResponseEntity<ApiResponse<PageResponse<CustomerMissionHistoryResponse>>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.ok(historyService.listHistory(page, size)));
    }

    @GetMapping("/{missionId}")
    @Operation(summary = "Get customer-owned mission history detail")
    public ResponseEntity<ApiResponse<CustomerMissionHistoryResponse>> get(@PathVariable String missionId) {
        return ResponseEntity.ok(ApiResponse.ok(historyService.getHistory(missionId)));
    }
}
