package com.ondemandmonitoring.Consultation.domains;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationRequirements {

    private String monitoringTarget;

    private String primaryGoal;

    @Builder.Default
    private List<String> problems = new ArrayList<>();

    private String scope;

    private String frequency;

    private String expectedOutcome;

    private String abnormalityHandling;

    private Boolean aiAnalysisRequested;

    @Builder.Default
    private List<String> additionalRequirements = new ArrayList<>();

    @Builder.Default
    private List<String> missingInformation = new ArrayList<>();
}
