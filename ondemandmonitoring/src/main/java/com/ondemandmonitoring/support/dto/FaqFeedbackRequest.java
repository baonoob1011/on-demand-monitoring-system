package com.ondemandmonitoring.support.dto;

import lombok.Data;

@Data
public class FaqFeedbackRequest {
    private String articleId;
    private String articleQuestion;
    private String articleCategory;
    private String customerId;
    private Boolean isHelpful;
}
