package com.ondemandmonitoring.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaqArticleDto {
    private String id;
    private String articleId;
    private String category;
    private String categoryLabel;
    private String question;
    private String answer;
    private List<String> keywords;
}
