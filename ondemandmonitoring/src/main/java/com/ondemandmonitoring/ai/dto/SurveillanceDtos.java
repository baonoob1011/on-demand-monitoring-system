package com.ondemandmonitoring.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ondemandmonitoring.order.dto.GeoJsonPointDto;
import com.ondemandmonitoring.order.enums.MediaTypeSp;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

public class SurveillanceDtos {

    // Request từ Frontend gửi lên API kiểm tra
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Request object for AI surveillance analysis, matching OrderCreateRequest")
    public static class SurveillanceAnalysisRequest {

        @NotBlank(message = "Title is required")
        @Schema(description = "Title of the order", example = "Forest Area Monitoring")
        private String title;

        @Schema(description = "Purpose of the monitoring order", example = "Wildfire risk detection")
        private String purpose;

        @NotBlank(message = "Service ID is required")
        @Schema(description = "Category Service ID", example = "550e8400-e29b-41d4-a716-446655440000")
        private String serviceId;

        @Schema(description = "Detailed description of the monitoring request")
        private String description;

        @Schema(description = "Address of the monitoring target location")
        private String address;

        @Valid
        @NotNull(message = "Point location is required")
        @Schema(description = "GeoJSON Point location format")
        private GeoJsonPointDto point;

        @NotNull(message = "Preferred date is required")
        @Schema(description = "Preferred monitoring date", example = "2026-10-01")
        private LocalDate preferredDate;

        @NotBlank(message = "Preferred time ID is required")
        @Schema(description = "Preferred time frame ID")
        private String preferredTimeId;

        @NotNull(message = "Media type is required")
        @Schema(description = "Media output type: IMAGE or VIDEO")
        private MediaTypeSp mediaType;

        @Schema(description = "Duration of video in seconds (Required if mediaType is VIDEO)", example = "120")
        private Integer durationOfVideo;

        @Schema(description = "Number of photos (Required if mediaType is IMAGE)", example = "10")
        private Integer numberOfPhoto;
    }

    // Kết quả phân tích trả về cho Frontend
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisResult {
        @JsonProperty("status")
        private String status; // "PASSED", "WARNING", "REJECTED"

        @JsonProperty("is_feasible")
        private boolean isFeasible;

        @JsonProperty("summary")
        private String summary;

        @JsonProperty("issues")
        private List<IssueDetail> issues;

        @JsonProperty("suggestions")
        private List<String> suggestions;

        @JsonProperty("refined_description")
        private String refinedDescription;

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class IssueDetail {
            @JsonProperty("field")
            private String field; // "service", "startDateTime", "mediaType", "description"

            @JsonProperty("issue_type")
            private String issueType;

            @JsonProperty("message")
            private String message;
        }
    }

    // Wrapper DTO giao tiếp với OpenRouter
    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OpenRouterRequest {
        private List<String> models;
        private List<Message> messages;
        private Double temperature;
        @JsonProperty("response_format")
        private ResponseFormat responseFormat; // Ép OpenRouter trả về JSON Object
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ResponseFormat {
        private String type; // "json_object"
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenRouterResponse {
        private String model;
        private List<Choice> choices;

        @Getter
        @Setter
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Choice {
            private Message message;
        }
    }
}