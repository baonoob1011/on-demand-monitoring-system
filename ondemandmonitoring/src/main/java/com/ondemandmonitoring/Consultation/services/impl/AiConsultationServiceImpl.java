package com.ondemandmonitoring.Consultation.services.impl;

import com.ondemandmonitoring.Consultation.domains.ConsultationMessage;
import com.ondemandmonitoring.Consultation.domains.CustomerConsultation;
import com.ondemandmonitoring.Consultation.dtos.responses.AiConsultationResult;
import com.ondemandmonitoring.Consultation.services.AiConsultationService;
import com.ondemandmonitoring.Consultation.services.ConsultationPromptTemplateService;
import com.ondemandmonitoring.Consultation.services.RagKnowledgeSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiConsultationServiceImpl implements AiConsultationService {

    private final ChatClient chatClient;
    private final RagKnowledgeSearchService ragKnowledgeSearchService;
    private final ConsultationPromptTemplateService promptTemplateService;


    @Override
    public AiConsultationResult respond(
            CustomerConsultation consultation,
            List<ConsultationMessage> history
    ) {

        if (history == null || history.isEmpty()) {
            throw new IllegalArgumentException(
                    "Lịch sử tư vấn không được để trống"
            );
        }

        String conversationContext =
                buildConversationContext(history);

        log.info(
                "Bắt đầu AI consultation. consultationId={}, số lượng messages={}",
                consultation.getId(),
                history.size()
        );

        // Bước 1: Tìm các Service phù hợp nhất từ RAG.
        List<Document> serviceDocuments =
                ragKnowledgeSearchService.searchServices(
                        conversationContext
                );

        // Bước 2: Chuyển các Document thành context cho LLM.
        String knowledgeContext =
                buildKnowledgeContext(serviceDocuments);

        log.info(
                "RAG tìm thấy {} service ứng viên cho consultationId={}",
                serviceDocuments.size(),
                consultation.getId()
        );

        // Bước 3: Cho Chat Model phân tích conversation + RAG knowledge.
        String userTaskPrompt = promptTemplateService.render(
                ConsultationPromptTemplateService.AI_USER_TASK_PROMPT,
                Map.of(
                        "conversationContext", conversationContext,
                        "knowledgeContext", knowledgeContext
                )
        );

        AiConsultationResult result =
                chatClient.prompt()
                        .system(promptTemplateService.getRequired(ConsultationPromptTemplateService.AI_SYSTEM_PROMPT))
                        .user(userTaskPrompt)
                        .call()
                        .entity(AiConsultationResult.class);

        if (result == null) {
            throw new IllegalStateException(
                    "AI không trả về kết quả tư vấn"
            );
        }

        log.info(
                "AI consultation hoàn thành. consultationId={}, status={}, recommendedServiceId={}",
                consultation.getId(),
                result.requirementStatus(),
                result.recommendedServiceId()
        );

        return result;
    }

    /**
     * Chuyển lịch sử message thành context để AI hiểu toàn bộ cuộc hội thoại.
     *
     * Ví dụ:
     *
     * CUSTOMER: Tôi có một vườn cà phê.
     * ASSISTANT: Bạn đang muốn theo dõi vấn đề gì?
     * CUSTOMER: Một số cây phát triển không đều.
     */
    private String buildConversationContext(
            List<ConsultationMessage> history
    ) {

        return history.stream()
                .filter(message ->
                        message.getMessage() != null
                                && !message.getMessage().isBlank()
                )
                .map(message ->
                        message.getSenderType().name()
                                + ": "
                                + message.getMessage().trim()
                )
                .collect(Collectors.joining("\n"));
    }

    /**
     * Chuyển kết quả RAG thành knowledge context cho AI.
     *
     * Metadata được giữ lại vì chứa serviceId thật.
     * AI phải sử dụng chính xác ID này khi đề xuất Service.
     */
    private String buildKnowledgeContext(
            List<Document> documents
    ) {

        if (documents == null || documents.isEmpty()) {
            return "Không tìm thấy dịch vụ phù hợp trong cơ sở tri thức.";
        }

        return documents.stream()
                .map(document -> """
                        ---
                        THÔNG TIN DỊCH VỤ

                        %s

                        METADATA
                        %s
                        """.formatted(
                        document.getText(),
                        document.getMetadata()
                ))
                .collect(Collectors.joining("\n"));
    }
}
