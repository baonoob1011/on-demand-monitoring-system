package com.ondemandmonitoring.Consultation.services;

import com.ondemandmonitoring.Consultation.repositories.ConsultationPromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConsultationPromptTemplateService {

    public static final String AI_SYSTEM_PROMPT = "AI_SYSTEM_PROMPT";
    public static final String AI_USER_TASK_PROMPT = "AI_USER_TASK_PROMPT";

    private final ConsultationPromptTemplateRepository templateRepository;

    public String getRequired(String key) {
        return templateRepository.findByTemplateKeyAndActiveTrue(key)
                .map(template -> template.getContent().trim())
                .filter(content -> !content.isBlank())
                .orElseThrow(() -> new IllegalStateException("Missing active consultation prompt template: " + key));
    }

    public String render(String key, Map<String, String> variables) {
        String content = getRequired(key);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            content = content.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return content.trim();
    }
}
