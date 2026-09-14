package com.ondemandmonitoring.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

public class SurveillanceDtos {

    // Request từ Frontend gửi lên API kiểm tra
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SurveillanceAnalysisRequest {
        private String title;
        private String service; // "giám sát công trình", "giám sát nông nghiệp", "giám sát thiên tai"
        private String purpose; // Mục tiêu cụ thể cần giám sát
        private String description; // Miêu tả chi tiết những gì cần giám sát
        private LocalDateTime startDateTime; // Thời gian bắt đầu
        private String mediaType; // "IMAGE", "VIDEO"
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