package com.ondemandmonitoring.ai.controller;

import com.ondemandmonitoring.ai.dto.SurveillanceDtos.*;
import com.ondemandmonitoring.ai.service.SurveillanceAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI Surveillance Management", description = "APIs for AI-assisted order request validation and feasibility analysis")
@RestController
@RequestMapping("/api/surveillance")
public class SurveillanceController {

    private final SurveillanceAnalysisService analysisService;

    public SurveillanceController(SurveillanceAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Operation(summary = "Validate surveillance request", description = "Validates an order request using AI to check feasibility, category service relevance, time slot, and media settings")
    @PostMapping("/validate-request")
    public ResponseEntity<AnalysisResult> validateRequest(@Valid @RequestBody SurveillanceAnalysisRequest request) {
        AnalysisResult result = analysisService.analyzeRequest(request);
        return ResponseEntity.ok(result);
    }
}