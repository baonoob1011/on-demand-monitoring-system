package com.ondemandmonitoring.Consultation.controllers;

import com.ondemandmonitoring.Consultation.dtos.requests.SendConsultationMessageRequest;
import com.ondemandmonitoring.Consultation.dtos.responses.CustomerConsultationResponse;
import com.ondemandmonitoring.Consultation.services.CustomerConsultationService;
import com.ondemandmonitoring.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/consultations")
@RequiredArgsConstructor
@Slf4j
@Tag(
        name = "Customer Consultation",
        description = "AI consultation APIs for customers before creating monitoring requests"
)
public class CustomerConsultationController {

    private final CustomerConsultationService consultationService;

    // =========================================================
    // START CONSULTATION
    // =========================================================

    @PostMapping
    @Operation(
            summary = "Start consultation",
            description = "Start a new AI consultation session for the authenticated customer"
    )
    public ResponseEntity<ApiResponse<CustomerConsultationResponse>> startConsultation() {

        log.info("Start consultation request received");

        CustomerConsultationResponse response =
                consultationService.startConsultation();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(
                        "Consultation started",
                        response
                ));
    }

    // =========================================================
    // GET CONSULTATION
    // =========================================================

    @GetMapping("/{consultationId}")
    @Operation(
            summary = "Get consultation",
            description = "Get a consultation and its conversation history"
    )
    public ResponseEntity<ApiResponse<CustomerConsultationResponse>> getConsultation(
            @PathVariable String consultationId
    ) {

        log.info(
                "Get consultation request received. consultationId={}",
                consultationId
        );

        CustomerConsultationResponse response =
                consultationService.getConsultation(
                        consultationId
                );

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // =========================================================
    // SEND MESSAGE
    // =========================================================

    @PostMapping("/{consultationId}/messages")
    @Operation(
            summary = "Send consultation message",
            description = "Send a customer message to the AI monitoring solution consultant"
    )
    public ResponseEntity<ApiResponse<CustomerConsultationResponse>> sendMessage(
            @PathVariable String consultationId,
            @Valid
            @RequestBody SendConsultationMessageRequest request
    ) {

        log.info(
                "Send consultation message request received. consultationId={}, messageLength={}",
                consultationId,
                request.getMessage() == null ? 0 : request.getMessage().length()
        );

        CustomerConsultationResponse response =
                consultationService.sendMessage(
                        consultationId,
                        request
                );

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
