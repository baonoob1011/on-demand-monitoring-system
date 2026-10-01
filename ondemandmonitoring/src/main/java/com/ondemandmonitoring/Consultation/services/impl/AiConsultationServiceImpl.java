package com.ondemandmonitoring.Consultation.services.impl;

import com.ondemandmonitoring.Consultation.domains.ConsultationMessage;
import com.ondemandmonitoring.Consultation.domains.ConsultationRequirements;
import com.ondemandmonitoring.Consultation.domains.CustomerConsultation;
import com.ondemandmonitoring.Consultation.dtos.responses.AiConsultationResult;
import com.ondemandmonitoring.Consultation.dtos.responses.ServiceSearchCandidate;
import com.ondemandmonitoring.Consultation.enums.ConsultationStatus;
import com.ondemandmonitoring.Consultation.services.AiConsultationService;
import com.ondemandmonitoring.Consultation.services.ConsultationPromptTemplateService;
import com.ondemandmonitoring.Consultation.services.RagKnowledgeSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiConsultationServiceImpl implements AiConsultationService {

    private final ChatClient chatClient;
    private final RagKnowledgeSearchService ragKnowledgeSearchService;
    private final ConsultationPromptTemplateService promptTemplateService;

    @Value("${consultation.rag.recommendation.min-score:0.72}")
    private double recommendationMinScore;

    @Value("${consultation.rag.recommendation.min-score-gap:0.08}")
    private double recommendationMinScoreGap;

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
        String latestCustomerText =
                latestCustomerText(conversationContext);

        Optional<AiConsultationResult> optionalAiAnalysisResponse =
                buildAiAnalysisOptionResponse(
                        consultation,
                        latestCustomerText
                );
        if (optionalAiAnalysisResponse.isPresent()) {
            return optionalAiAnalysisResponse.get();
        }

        log.info(
                "Bắt đầu AI consultation. consultationId={}, số lượng messages={}",
                consultation.getId(),
                history.size()
        );

        // Bước 1: Tìm các Service phù hợp nhất từ RAG.
        List<ServiceSearchCandidate> serviceCandidates =
                ragKnowledgeSearchService.searchServices(
                        conversationContext
                );

        Optional<AiConsultationResult> directRecommendation =
                buildRagRecommendation(
                        latestCustomerText,
                        extractCustomerEvidence(conversationContext),
                        serviceCandidates,
                        consultation.getId()
                );
        if (directRecommendation.isPresent()) {
            return directRecommendation.get();
        }

        if (serviceCandidates == null || serviceCandidates.isEmpty()) {
            log.info(
                    "RAG decision. consultationId={}, decision=NEED_MORE_INFO, reason=no_service_candidate",
                    consultation.getId()
            );
            return needMoreInfo(
                    "Mình chưa đủ thông tin để chọn đúng dịch vụ. Anh/chị cho biết đối tượng cần giám sát là gì và mục tiêu chính muốn kiểm tra là tiến độ, hiện trạng, an toàn hay dấu hiệu bất thường nhé.",
                    latestCustomerText
            );
        }

        // Bước 2: Chuyển các Document thành context cho LLM.
        String knowledgeContext =
                buildKnowledgeContext(serviceCandidates);

        log.info(
                "RAG tìm thấy {} service ứng viên cho consultationId={}",
                serviceCandidates.size(),
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

        result = enforceRetrievedServiceRecommendation(
                result,
                serviceCandidates,
                consultation.getId()
        );

        log.info(
                "AI consultation hoàn thành. consultationId={}, status={}, recommendedServiceId={}",
                consultation.getId(),
                result.requirementStatus(),
                result.recommendedServiceId()
        );

        return result;
    }

    private AiConsultationResult enforceRetrievedServiceRecommendation(
            AiConsultationResult result,
            List<ServiceSearchCandidate> retrievedServices,
            String consultationId
    ) {

        String recommendedServiceId = result.recommendedServiceId();
        if (recommendedServiceId == null || recommendedServiceId.isBlank()) {
            return result;
        }

        Set<String> retrievedServiceIds = retrievedServices.stream()
                .map(ServiceSearchCandidate::serviceId)
                .collect(Collectors.toSet());

        if (retrievedServiceIds.contains(recommendedServiceId.trim())) {
            return result;
        }

        log.warn(
                "AI returned serviceId outside retrieved RAG services. consultationId={}, serviceId={}, retrievedServiceIds={}",
                consultationId,
                recommendedServiceId.trim(),
                retrievedServiceIds
        );

        return new AiConsultationResult(
                """
                        Hiện chưa tìm thấy dịch vụ phù hợp với nhu cầu này. Anh/chị có thể mô tả cụ thể hơn mục tiêu cần giám sát.
                        """.trim(),
                ConsultationStatus.NEED_MORE_INFO,
                null,
                result.requirementSummary(),
                null,
                null,
                result.requirements()
        );
    }

    private Optional<AiConsultationResult> buildRagRecommendation(
            String latestCustomerText,
            String customerEvidence,
            List<ServiceSearchCandidate> candidates,
            String consultationId
    ) {

        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        ServiceSearchCandidate semanticBest = candidates.get(0);
        ServiceSearchCandidate best = strongestCustomerEvidenceMatch(
                candidates,
                customerEvidence
        ).orElse(semanticBest);
        if (best.score() < recommendationMinScore) {
            log.info(
                    "RAG decision. consultationId={}, decision=AMBIGUOUS, reason=score_below_threshold, topScore={}, minScore={}",
                    consultationId,
                    best.score(),
                    recommendationMinScore
            );
            return Optional.empty();
        }

        if (candidates.size() > 1) {
            ServiceSearchCandidate second = candidates.stream()
                    .filter(candidate -> !candidate.serviceId().equals(best.serviceId()))
                    .findFirst()
                    .orElse(candidates.get(1));
            double gap = best.score() - second.score();
            boolean hasDirectCustomerMatch = customerEvidenceMatchScore(
                    best,
                    customerEvidence
            ) >= 2;
            if (gap < recommendationMinScoreGap
                    && !hasDirectCustomerMatch) {
                log.info(
                        "RAG decision. consultationId={}, decision=AMBIGUOUS, reason=score_gap_below_threshold, topScore={}, secondScore={}, gap={}, minGap={}",
                        consultationId,
                        best.score(),
                        second.score(),
                        gap,
                        recommendationMinScoreGap
                );
                return Optional.empty();
            }
            if (!best.serviceId().equals(semanticBest.serviceId())) {
                log.info(
                        "RAG decision. consultationId={}, decision=RERANKED_BY_CUSTOMER_EVIDENCE, semanticServiceId={}, selectedServiceId={}, selectedServiceName={}, selectedScore={}",
                        consultationId,
                        semanticBest.serviceId(),
                        best.serviceId(),
                        best.serviceName(),
                        best.score()
                );
            } else if (gap < recommendationMinScoreGap) {
                log.info(
                        "RAG decision. consultationId={}, decision=RECOMMENDED_BY_CUSTOMER_EVIDENCE, serviceId={}, serviceName={}, score={}, secondScore={}, gap={}",
                        consultationId,
                        best.serviceId(),
                        best.serviceName(),
                        best.score(),
                        second.score(),
                        gap
                );
            }
        }

        if (latestCustomerText == null || latestCustomerText.isBlank()) {
            return Optional.empty();
        }

        String message = """
                Mình hiểu bạn đang cần: %s.

                Dịch vụ phù hợp nhất là **%s** vì dịch vụ này đáp ứng đúng nhóm nhu cầu bạn vừa mô tả.

                Bạn có muốn bổ sung AI phân tích hình ảnh để tự động phát hiện và đánh dấu dấu hiệu bất thường không? Đây là lựa chọn bổ sung và có thể phát sinh thêm chi phí.
                """
                .formatted(
                        latestCustomerText.trim(),
                        best.serviceName()
                )
                .trim();

        log.info(
                "RAG decision. consultationId={}, decision=RECOMMENDED, serviceId={}, serviceName={}, score={}",
                consultationId,
                best.serviceId(),
                best.serviceName(),
                best.score()
        );

        return Optional.of(new AiConsultationResult(
                message,
                ConsultationStatus.RECOMMENDED,
                best.serviceId(),
                latestCustomerText.trim(),
                buildRequestTitle(best, latestCustomerText),
                buildRequestDescription(best, latestCustomerText),
                null
        ));
    }

    private Optional<ServiceSearchCandidate> strongestCustomerEvidenceMatch(
            List<ServiceSearchCandidate> candidates,
            String customerEvidence
    ) {
        return candidates.stream()
                .filter(candidate -> candidate.score() >= recommendationMinScore)
                .map(candidate -> Map.entry(
                        candidate,
                        customerEvidenceMatchScore(candidate, customerEvidence)
                ))
                .filter(entry -> entry.getValue() >= 2)
                .max((left, right) -> {
                    int scoreCompare = Integer.compare(
                            left.getValue(),
                            right.getValue()
                    );
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    return Double.compare(
                            left.getKey().score(),
                            right.getKey().score()
                    );
                })
                .map(Map.Entry::getKey);
    }

    private String extractCustomerEvidence(String conversationContext) {
        if (conversationContext == null || conversationContext.isBlank()) {
            return "";
        }

        StringBuilder evidence = new StringBuilder();
        boolean insideServiceCatalog = false;

        for (String line : conversationContext.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.contains("Danh sách service active từ BE:")) {
                insideServiceCatalog = true;
                continue;
            }

            if (insideServiceCatalog) {
                if (trimmed.startsWith("CUSTOMER:")
                        || trimmed.startsWith("ASSISTANT:")) {
                    insideServiceCatalog = false;
                } else {
                    continue;
                }
            }

            if (trimmed.startsWith("ASSISTANT:")) {
                continue;
            }

            evidence.append(trimmed).append('\n');
        }

        return evidence.toString();
    }

    private int customerEvidenceMatchScore(
            ServiceSearchCandidate candidate,
            String customerEvidence
    ) {
        String evidence = " " + normalizeAnswer(customerEvidence) + " ";
        if (evidence.isBlank()) {
            return 0;
        }

        List<String> serviceTokens = meaningfulTokens(
                candidate.serviceName() + " " + candidate.content()
        );
        if (serviceTokens.isEmpty()) {
            return 0;
        }

        int score = 0;
        for (String token : serviceTokens.stream().distinct().toList()) {
            if (evidence.contains(" " + token + " ")) {
                score++;
            }
        }

        for (int index = 0; index < serviceTokens.size() - 1; index++) {
            String phrase = serviceTokens.get(index)
                    + " "
                    + serviceTokens.get(index + 1);
            if (evidence.contains(" " + phrase + " ")) {
                score += 2;
            }
        }

        return score;
    }

    private List<String> meaningfulTokens(String text) {
        Set<String> ignoredWords = Set.of(
                "ai", "anh", "bao", "bang", "ban", "cao", "can", "chon",
                "co", "cua", "de", "dich", "du", "duoc", "gia", "giam",
                "ghi", "hien", "ho", "khach", "khu", "kiem", "la", "lai",
                "lam", "luu", "mot", "muon", "nay", "nhu", "nhan", "nhat",
                "phu", "qua", "quan", "sat", "service", "theo", "thong",
                "tra", "tu", "van", "video", "vung", "yeu"
        );

        return List.of(normalizeAnswer(text).split("\\s+")).stream()
                .filter(token -> token.length() >= 3)
                .filter(token -> !ignoredWords.contains(token))
                .toList();
    }

    private String latestCustomerText(String conversationContext) {

        if (conversationContext == null || conversationContext.isBlank()) {
            return "";
        }

        String latest = "";
        for (String line : conversationContext.split("\\R")) {
            if (line.startsWith("CUSTOMER: ")) {
                latest = line.substring("CUSTOMER: ".length()).trim();
            }
        }
        return latest;
    }

    private AiConsultationResult needMoreInfo(String reply, String latestCustomerText) {
        return new AiConsultationResult(
                reply,
                ConsultationStatus.NEED_MORE_INFO,
                null,
                latestCustomerText == null || latestCustomerText.isBlank()
                        ? null
                        : latestCustomerText.trim(),
                null,
                null,
                null
        );
    }

    private Optional<AiConsultationResult> buildAiAnalysisOptionResponse(
            CustomerConsultation consultation,
            String latestCustomerText
    ) {
        if (consultation == null
                || consultation.getRecommendedService() == null
                || latestCustomerText == null
                || latestCustomerText.isBlank()) {
            return Optional.empty();
        }

        Optional<Boolean> answer = parseYesNo(latestCustomerText);
        if (answer.isEmpty()) {
            return Optional.empty();
        }

        boolean requested = answer.get();
        String reply = requested
                ? "Đã ghi nhận yêu cầu bổ sung AI phân tích hình ảnh. Service chính đã đề xuất vẫn được giữ nguyên."
                : "Đã ghi nhận không thêm yêu cầu bổ sung AI phân tích hình ảnh. Service chính đã đề xuất vẫn được giữ nguyên.";

        String summary = consultation.getRequirementSummary();
        if (requested) {
            summary = appendAiAnalysisSummary(summary);
        }

        return Optional.of(new AiConsultationResult(
                reply,
                ConsultationStatus.RECOMMENDED,
                consultation.getRecommendedService().getId(),
                summary,
                consultation.getRequestTitle(),
                requested
                        ? appendAiAnalysisDescription(consultation.getRequestSummary())
                        : consultation.getRequestSummary(),
                ConsultationRequirements.builder()
                        .aiAnalysisRequested(requested)
                        .additionalRequirements(requested
                                ? List.of("AI phân tích hình ảnh để hỗ trợ phát hiện và đánh dấu các dấu hiệu bất thường.")
                                : List.of())
                        .build()
        ));
    }

    private Optional<Boolean> parseYesNo(String text) {
        String normalized = normalizeAnswer(text);
        if (normalized.matches("^(co|ok|okay|duoc|can|yes|y)\\b.*")) {
            return Optional.of(true);
        }
        if (normalized.matches("^(khong|ko|k|no|n|thoi)\\b.*")) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    private String normalizeAnswer(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "d")
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String buildRequestTitle(ServiceSearchCandidate service, String latestCustomerText) {
        String base = latestCustomerText == null || latestCustomerText.isBlank()
                ? service.serviceName()
                : latestCustomerText.trim();
        return base.length() > 80 ? base.substring(0, 80).trim() : base;
    }

    private String buildRequestDescription(ServiceSearchCandidate service, String latestCustomerText) {
        String customerNeed = latestCustomerText == null || latestCustomerText.isBlank()
                ? "nhu cầu đã cung cấp"
                : latestCustomerText.trim();
        return "Gợi ý service " + service.serviceName()
                + " dựa trên nhu cầu: " + customerNeed
                + ". Ghi nhận dữ liệu khu vực giám sát và bàn giao theo các deliverable đã cấu hình cho service.";
    }

    private String appendAiAnalysisSummary(String summary) {
        String addon = "Yêu cầu bổ sung: AI phân tích hình ảnh để hỗ trợ phát hiện và đánh dấu các dấu hiệu bất thường.";
        if (summary == null || summary.isBlank()) {
            return addon;
        }
        if (summary.contains("AI hỗ trợ phân tích hình ảnh")) {
            return summary;
        }
        return summary.trim() + "\n" + addon;
    }

    private String appendAiAnalysisDescription(String description) {
        String addon = "Yêu cầu bổ sung: sử dụng AI phân tích hình ảnh để hỗ trợ phát hiện và đánh dấu các dấu hiệu bất thường.";
        if (description == null || description.isBlank()) {
            return addon;
        }
        if (description.contains("AI hỗ trợ phân tích hình ảnh")) {
            return description;
        }
        return description.trim() + "\n" + addon;
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
            List<ServiceSearchCandidate> candidates
    ) {

        if (candidates == null || candidates.isEmpty()) {
            return "Không tìm thấy dịch vụ phù hợp trong cơ sở tri thức.";
        }

        return candidates.stream()
                .map(candidate -> """
                        ---
                        THÔNG TIN DỊCH VỤ

                        %s

                        METADATA
                        {type=SERVICE, serviceId=%s, serviceName=%s, score=%s}
                        """.formatted(
                        candidate.content(),
                        candidate.serviceId(),
                        candidate.serviceName(),
                        candidate.score()
                ))
                .collect(Collectors.joining("\n"));
    }
}
