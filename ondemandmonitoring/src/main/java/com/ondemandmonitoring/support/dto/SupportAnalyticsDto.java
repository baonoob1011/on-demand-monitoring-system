package com.ondemandmonitoring.support.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class SupportAnalyticsDto {
    private long totalHelpfulVotes;
    private long totalTickets;
    private double deflectionRate;
    private double avgResponseTimeMins;
    private double slaComplianceRate;
    private List<TopFaqDto> topHelpfulArticles;
    private Map<String, Double> categoryBreakdown;

    @Data
    @Builder
    public static class TopFaqDto {
        private String articleId;
        private String articleQuestion;
        private long votesCount;
    }
}
