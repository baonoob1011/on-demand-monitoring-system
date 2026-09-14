package com.ondemandmonitoring.ai.controller;

import com.ondemandmonitoring.ai.dto.SurveillanceDtos.*;
import com.ondemandmonitoring.ai.service.SurveillanceAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/surveillance")
public class SurveillanceController {

    private final SurveillanceAnalysisService analysisService;

    public SurveillanceController(SurveillanceAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/validate-request")
    public ResponseEntity<AnalysisResult> validateRequest(@RequestBody SurveillanceAnalysisRequest request) {
        AnalysisResult result = analysisService.analyzeRequest(request);
        return ResponseEntity.ok(result);
    }
}