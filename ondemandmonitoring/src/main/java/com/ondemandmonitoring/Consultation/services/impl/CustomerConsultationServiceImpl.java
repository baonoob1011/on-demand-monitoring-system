package com.ondemandmonitoring.Consultation.services.impl;

import com.ondemandmonitoring.Consultation.domains.ConsultationMessage;
import com.ondemandmonitoring.Consultation.domains.ConsultationLearningEntry;
import com.ondemandmonitoring.Consultation.domains.ConsultationRequirements;
import com.ondemandmonitoring.Consultation.domains.CustomerConsultation;
import com.ondemandmonitoring.Consultation.dtos.requests.SendConsultationMessageRequest;
import com.ondemandmonitoring.Consultation.dtos.responses.AiConsultationResult;
import com.ondemandmonitoring.Consultation.dtos.responses.CustomerConsultationResponse;
import com.ondemandmonitoring.Consultation.enums.ConsultationSenderType;
import com.ondemandmonitoring.Consultation.enums.ConsultationStatus;
import com.ondemandmonitoring.Consultation.mappers.CustomerConsultationMapper;
import com.ondemandmonitoring.Consultation.repositories.ConsultationLearningEntryRepository;
import com.ondemandmonitoring.Consultation.repositories.ConsultationMessageRepository;
import com.ondemandmonitoring.Consultation.repositories.CustomerConsultationRepository;
import com.ondemandmonitoring.Consultation.services.AiConsultationService;
import com.ondemandmonitoring.Consultation.services.ConsultationPromptTemplateService;
import com.ondemandmonitoring.Consultation.services.CustomerConsultationService;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
public class CustomerConsultationServiceImpl
        implements CustomerConsultationService {

    private final CustomerConsultationRepository consultationRepository;
    private final ConsultationMessageRepository messageRepository;
    private final ConsultationLearningEntryRepository learningEntryRepository;
    private final CustomerConsultationMapper consultationMapper;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final AiConsultationService aiConsultationService;
    private final ConsultationPromptTemplateService promptTemplateService;
    private final ServiceRepository serviceRepository;
    private final ObjectMapper objectMapper;

    private static final Duration AI_REPLY_TIMEOUT =
            Duration.ofSeconds(30);
    // =========================================================
    // START CONSULTATION
    // =========================================================

    @Override
    @Transactional
    public CustomerConsultationResponse startConsultation() {

        User customer = getCurrentCustomer();

        CustomerConsultation consultation =
                CustomerConsultation.builder()
                        .customer(customer)
                        .status(ConsultationStatus.ACTIVE)
                        .build();

        CustomerConsultation saved =
                consultationRepository.save(consultation);

        return consultationMapper.toResponse(
                saved,
                List.of()
        );
    }

    // =========================================================
    // GET CONSULTATION
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public CustomerConsultationResponse getConsultation(
            String consultationId
    ) {

        User customer = getCurrentCustomer();

        CustomerConsultation consultation =
                getOwnedConsultation(
                        consultationId,
                        customer.getId()
                );

        List<ConsultationMessage> messages =
                messageRepository
                        .findByConsultationIdOrderByCreatedAtAsc(
                                consultationId
                        );

        return consultationMapper.toResponse(
                consultation,
                messages
        );
    }

    // =========================================================
    // SEND MESSAGE
    // =========================================================

    @Override
    @Transactional
    public CustomerConsultationResponse sendMessage(
            String consultationId,
            SendConsultationMessageRequest request
    ) {

        User customer = getCurrentCustomer();

        CustomerConsultation consultation =
                getOwnedConsultation(
                        consultationId,
                        customer.getId()
                );

        /*
         * ACTIVE:
         *      Customer vẫn đang trao đổi với AI.
         *
         * READY_FOR_CONFIRMATION:
         *      Customer vẫn phải được gửi message để:
         *      - xác nhận
         *      - chỉnh sửa
         *      - bổ sung requirement
         *
         * CONFIRMED / CANCELLED:
         *      Consultation đã kết thúc.
         */
        if (consultation.getStatus() == ConsultationStatus.CONFIRMED
                || consultation.getStatus() == ConsultationStatus.CANCELLED) {

            throw new IllegalStateException(
                    "Consultation is already closed"
            );
        }

        // =====================================================
        // 1. SAVE CUSTOMER MESSAGE
        // =====================================================

        ConsultationMessage customerMessage =
                ConsultationMessage.builder()
                        .consultation(consultation)
                        .senderType(
                                ConsultationSenderType.CUSTOMER
                        )
                        .message(
                                request.getMessage().trim()
                        )
                        .build();

        messageRepository.save(customerMessage);
        saveLearningEntry(consultation, customer, request.getMessage(), null);

        // =====================================================
        // 2. LOAD CONVERSATION HISTORY
        // =====================================================

        List<ConsultationMessage> history =
                messageRepository
                        .findByConsultationIdOrderByCreatedAtAsc(
                                consultationId
                        );
        List<ConsultationMessage> aiHistory =
                buildAiHistoryWithRequestContext(
                        consultation,
                        history,
                        request.getRequestContext()
                );

        // =====================================================
        // 3. CALL AI CONSULTANT
        // =====================================================

        AiConsultationResult aiResult;

        try {
            aiResult = requestAiConsultation(
                    consultation,
                    aiHistory
            );
        } catch (TimeoutException exception) {
            log.warn(
                    "AI consultation timed out after {} seconds. consultationId={}, customerId={}",
                    AI_REPLY_TIMEOUT.toSeconds(),
                    consultationId,
                    customer.getId()
            );

            return saveFallbackAssistantReply(
                    consultation,
                    consultationId,
                    aiHistory
            );
        } catch (Exception exception) {
            log.error(
                    "AI consultation failed. consultationId={}, customerId={}",
                    consultationId,
                    customer.getId(),
                    exception
            );

            return saveFallbackAssistantReply(
                    consultation,
                    consultationId,
                    aiHistory
            );
        }

        try {
            if (aiResult == null) {
                log.warn(
                        "AI consultation returned no result. consultationId={}, customerId={}",
                        consultationId,
                        customer.getId()
                );

                return saveFallbackAssistantReply(
                        consultation,
                        consultationId,
                        aiHistory
                );
            }

            // =====================================================
            // 4. VALIDATE AI REPLY
            // =====================================================

            if (aiResult.reply() == null
                    || aiResult.reply().isBlank()) {

                log.warn(
                        "AI returned an empty reply. consultationId={}, customerId={}",
                        consultationId,
                        customer.getId()
                );

                return saveFallbackAssistantReply(
                        consultation,
                        consultationId,
                        aiHistory
                );
            }

            // =====================================================
            // 5. UPDATE STRUCTURED REQUIREMENTS
            // =====================================================

            if (aiResult.requirements() != null) {

                consultation.setRequirementData(
                        serializeRequirements(
                                aiResult.requirements()
                        )
                );
            }

            // =====================================================
            // 6. UPDATE REQUIREMENT SUMMARY
            // =====================================================

            if (aiResult.requirementSummary() != null
                    && !aiResult.requirementSummary().isBlank()) {

                consultation.setRequirementSummary(
                        aiResult.requirementSummary().trim()
                );
            }

            // =====================================================
            // 7. VALIDATE RECOMMENDED SERVICE
            // =====================================================

            Optional<Service> recommendedServiceOptional =
                    resolveRecommendedService(aiResult);
            String assistantReply = aiResult.reply().trim();

            if (recommendedServiceOptional.isPresent()) {
                Service recommendedService = recommendedServiceOptional.get();

                consultation.setRecommendedService(
                        recommendedService
                );
                saveLearningEntry(consultation, customer, request.getMessage(), recommendedService);

            } else if (aiResult.recommendedServiceId() != null
                    && !aiResult.recommendedServiceId().isBlank()) {
                log.warn(
                        "AI returned invalid or inactive serviceId. consultationId={}, serviceId={}",
                        consultationId,
                        aiResult.recommendedServiceId().trim()
                );
                consultation.setRecommendedService(null);
            } else {
                consultation.setRecommendedService(null);
            }

            // =====================================================
            // 8. UPDATE CONSULTATION STATUS
            // =====================================================

            /*
             * Không tin hoàn toàn status do LLM trả về.
             *
             * AI hiện chỉ có quyền đưa consultation về:
             *
             * ACTIVE / NEED_MORE_INFO
             * RECOMMENDED
             * READY_FOR_CONFIRMATION
             *
             * CONFIRMED sẽ do backend xử lý riêng sau khi
             * Customer thực sự xác nhận.
             */
            if (aiResult.requirementStatus()
                    == ConsultationStatus.RECOMMENDED
                    && recommendedServiceOptional.isPresent()) {

                consultation.setStatus(
                        ConsultationStatus.RECOMMENDED
                );
                consultation.setRequestTitle(
                        normalizeGeneratedDraftText(aiResult.requestTitle())
                );
                consultation.setRequestSummary(
                        normalizeGeneratedDraftText(aiResult.requestSummary())
                );

            } else if (aiResult.requirementStatus()
                    == ConsultationStatus.READY_FOR_CONFIRMATION
                    && recommendedServiceOptional.isPresent()) {

                consultation.setStatus(
                        ConsultationStatus.READY_FOR_CONFIRMATION
                );
                consultation.setRequestTitle(
                        normalizeGeneratedDraftText(aiResult.requestTitle())
                );
                consultation.setRequestSummary(
                        normalizeGeneratedDraftText(aiResult.requestSummary())
                );

            } else {

                if (aiResult.requirementStatus()
                        == ConsultationStatus.READY_FOR_CONFIRMATION) {
                    assistantReply = """
                            Hiện chưa tìm thấy dịch vụ phù hợp với nhu cầu này. Anh/chị có thể mô tả cụ thể hơn mục tiêu cần giám sát.
                            """.trim();
                }

                consultation.setStatus(
                        aiResult.requirementStatus() == ConsultationStatus.NEED_MORE_INFO
                                ? ConsultationStatus.NEED_MORE_INFO
                                : ConsultationStatus.ACTIVE
                );
                consultation.setRequestTitle(null);
                consultation.setRequestSummary(null);
            }

            consultationRepository.save(consultation);

            // =====================================================
            // 9. SAVE ASSISTANT MESSAGE
            // =====================================================

            ConsultationMessage assistantMessage =
                    ConsultationMessage.builder()
                            .consultation(consultation)
                            .senderType(
                                    ConsultationSenderType.ASSISTANT
                            )
                            .message(
                                    assistantReply
                            )
                            .build();

            messageRepository.save(assistantMessage);
        } catch (Exception exception) {
            log.error(
                    "Failed to apply AI consultation result. consultationId={}, customerId={}",
                    consultationId,
                    customer.getId(),
                    exception
            );

            return saveFallbackAssistantReply(
                    consultation,
                    consultationId,
                    aiHistory
            );
        }

        // =====================================================
        // 10. LOAD UPDATED CONVERSATION
        // =====================================================

        List<ConsultationMessage> updatedMessages =
                messageRepository
                        .findByConsultationIdOrderByCreatedAtAsc(
                                consultationId
                        );

        // =====================================================
        // 11. RETURN RESPONSE
        // =====================================================

        return consultationMapper.toResponse(
                consultation,
                updatedMessages
        );
    }

    // =========================================================
    // SERIALIZE REQUIREMENTS
    // =========================================================

    private String serializeRequirements(
            ConsultationRequirements requirements
    ) {

        /*
         * Project đang dùng Jackson mới:
         *
         * tools.jackson.databind.ObjectMapper
         *
         * writeValueAsString() ở version này không bắt buộc
         * catch com.fasterxml.jackson.core.JsonProcessingException.
         */
        return objectMapper.writeValueAsString(
                requirements
        );
    }

    private AiConsultationResult requestAiConsultation(
            CustomerConsultation consultation,
            List<ConsultationMessage> history
    ) throws Exception {

        CompletableFuture<AiConsultationResult> aiCall =
                CompletableFuture.supplyAsync(() ->
                        aiConsultationService.respond(
                                consultation,
                                history
                        )
                );

        try {
            return aiCall.get(
                    AI_REPLY_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (TimeoutException exception) {
            aiCall.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception causeException) {
                throw causeException;
            }
            throw exception;
        }
    }

    private List<ConsultationMessage> buildAiHistoryWithRequestContext(
            CustomerConsultation consultation,
            List<ConsultationMessage> history,
            String requestContext
    ) {

        if (requestContext == null || requestContext.isBlank()) {
            return history;
        }

        ConsultationMessage contextMessage =
                ConsultationMessage.builder()
                        .consultation(consultation)
                        .senderType(ConsultationSenderType.CUSTOMER)
                        .message("""
                                BỐI CẢNH FORM TẠO REQUEST
                                Đây là thông tin đã nhập ở Step 1 và các lựa chọn hiện tại trên form.
                                Hãy dùng thông tin này để tư vấn, nhưng không hỏi lại nếu đã đủ rõ.

                                %s
                                """.formatted(requestContext.trim()))
                        .build();

        return java.util.stream.Stream
                .concat(java.util.stream.Stream.of(contextMessage), history.stream())
                .toList();
    }

    private CustomerConsultationResponse saveFallbackAssistantReply(
            CustomerConsultation consultation,
            String consultationId,
            List<ConsultationMessage> aiHistory
    ) {

        consultation.setStatus(ConsultationStatus.ACTIVE);
        consultation.setRequirementSummary(
                buildFallbackRequirementSummary(aiHistory)
        );
        consultationRepository.save(consultation);

        ConsultationMessage assistantMessage =
                ConsultationMessage.builder()
                        .consultation(consultation)
                        .senderType(
                                ConsultationSenderType.ASSISTANT
                        )
                        .message(
                                buildAiUnavailableReply(aiHistory)
                        )
                        .build();

        messageRepository.save(assistantMessage);

        List<ConsultationMessage> updatedMessages =
                messageRepository
                        .findByConsultationIdOrderByCreatedAtAsc(
                                consultationId
                        );

        return consultationMapper.toResponse(
                consultation,
                updatedMessages
        );
    }

    private String buildAiUnavailableReply(
            List<ConsultationMessage> aiHistory
    ) {
        return promptTemplateService.getRequired("FALLBACK_AI_UNAVAILABLE");
    }

    private String buildFallbackRequirementSummary(
            List<ConsultationMessage> aiHistory
    ) {
        String latestCustomerText = normalizeLatestCustomerMessage(aiHistory);
        if (latestCustomerText == null || latestCustomerText.isBlank()) {
            return "Khách hàng đang cần tư vấn dịch vụ giám sát. Cần làm rõ đối tượng/khu vực cần giám sát và mục tiêu chính trước khi đề xuất service.";
        }

        return "Khách hàng đang cần tư vấn dịch vụ giám sát dựa trên nhu cầu vừa cung cấp. Cần làm rõ thêm mục tiêu ưu tiên, phạm vi khu vực và nhu cầu phân tích hình ảnh trước khi đề xuất service.";
    }

    private Optional<Service> resolveRecommendedService(AiConsultationResult aiResult) {

        if (aiResult.recommendedServiceId() != null
                && !aiResult.recommendedServiceId().isBlank()) {

            Optional<Service> byId = serviceRepository.findById(
                    aiResult.recommendedServiceId().trim()
            );

            if (byId.isPresent()
                    && Boolean.TRUE.equals(byId.get().getIsActive())) {
                return byId;
            }
        }
        return Optional.empty();
    }

    private String normalizeGeneratedDraftText(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String normalizeHistory(List<ConsultationMessage> messages) {

        if (messages == null || messages.isEmpty()) {
            return "";
        }

        String combined = messages.stream()
                .filter(message -> message.getMessage() != null)
                .map(ConsultationMessage::getMessage)
                .collect(java.util.stream.Collectors.joining("\n"));

        return normalizeText(combined);
    }

    private String normalizeLatestCustomerMessage(List<ConsultationMessage> messages) {

        if (messages == null || messages.isEmpty()) {
            return "";
        }

        for (int index = messages.size() - 1; index >= 0; index--) {
            ConsultationMessage message = messages.get(index);
            if (message.getSenderType() == ConsultationSenderType.CUSTOMER
                    && message.getMessage() != null
                    && !message.getMessage().isBlank()
                    && !message.getMessage().contains("BỐI CẢNH FORM TẠO REQUEST")) {
                return normalizeText(message.getMessage());
            }
        }

        return "";
    }

    private String normalizeText(String text) {

        if (text == null) {
            return "";
        }

        return Normalizer
                .normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "d")
                .toLowerCase();
    }

    private void saveLearningEntry(
            CustomerConsultation consultation,
            User customer,
            String rawMessage,
            Service service
    ) {

        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }

        String normalized = normalizeText(rawMessage)
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.length() < 5) {
            return;
        }

        String customerId = customer != null ? customer.getId() : null;
        if (customerId != null
                && learningEntryRepository.existsByCustomerIdAndNormalizedMessage(customerId, normalized)) {
            return;
        }

        ConsultationLearningEntry entry = new ConsultationLearningEntry();
        entry.setConsultation(consultation);
        entry.setCustomerId(customerId);
        entry.setRawMessage(rawMessage.trim());
        entry.setNormalizedMessage(normalized);
        entry.setService(service);
        entry.setPromotedToSuggestion(false);
        learningEntryRepository.save(entry);
    }

    // =========================================================
    // GET OWNED CONSULTATION
    // =========================================================

    private CustomerConsultation getOwnedConsultation(
            String consultationId,
            String customerId
    ) {

        CustomerConsultation consultation =
                consultationRepository
                        .findById(consultationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Consultation not found"
                                )
                        );

        if (consultation.getCustomer() == null
                || consultation.getCustomer().getId() == null
                || !consultation
                .getCustomer()
                .getId()
                .equals(customerId)) {

            /*
             * Không trả lỗi khác nhau giữa:
             *
             * - consultation không tồn tại
             * - consultation thuộc customer khác
             *
             * tránh làm lộ resource của user khác.
             */
            throw new IllegalArgumentException(
                    "Consultation not found"
            );
        }

        return consultation;
    }

    // =========================================================
    // GET CURRENT CUSTOMER
    // =========================================================

    private User getCurrentCustomer() {
        return authenticatedUserResolver.getCurrentUser();
    }
}
