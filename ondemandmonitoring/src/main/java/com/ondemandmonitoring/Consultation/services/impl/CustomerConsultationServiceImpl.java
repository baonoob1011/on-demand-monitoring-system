package com.ondemandmonitoring.Consultation.services.impl;

import com.ondemandmonitoring.Consultation.domains.ConsultationMessage;
import com.ondemandmonitoring.Consultation.domains.ConsultationRequirements;
import com.ondemandmonitoring.Consultation.domains.CustomerConsultation;
import com.ondemandmonitoring.Consultation.dtos.requests.SendConsultationMessageRequest;
import com.ondemandmonitoring.Consultation.dtos.responses.AiConsultationResult;
import com.ondemandmonitoring.Consultation.dtos.responses.CustomerConsultationResponse;
import com.ondemandmonitoring.Consultation.enums.ConsultationSenderType;
import com.ondemandmonitoring.Consultation.enums.ConsultationStatus;
import com.ondemandmonitoring.Consultation.mappers.CustomerConsultationMapper;
import com.ondemandmonitoring.Consultation.repositories.ConsultationMessageRepository;
import com.ondemandmonitoring.Consultation.repositories.CustomerConsultationRepository;
import com.ondemandmonitoring.Consultation.services.AiConsultationService;
import com.ondemandmonitoring.Consultation.services.CustomerConsultationService;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.user.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class CustomerConsultationServiceImpl
        implements CustomerConsultationService {

    private final CustomerConsultationRepository consultationRepository;
    private final ConsultationMessageRepository messageRepository;
    private final CustomerConsultationMapper consultationMapper;
    private final IUserService userService;
    private final AiConsultationService aiConsultationService;
    private final ServiceRepository serviceRepository;
    private final ObjectMapper objectMapper;
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

        // =====================================================
        // 2. LOAD CONVERSATION HISTORY
        // =====================================================

        List<ConsultationMessage> history =
                messageRepository
                        .findByConsultationIdOrderByCreatedAtAsc(
                                consultationId
                        );

        // =====================================================
        // 3. CALL AI CONSULTANT
        // =====================================================

        AiConsultationResult aiResult =
                aiConsultationService.respond(
                        consultation,
                        history
                );

        if (aiResult == null) {
            throw new IllegalStateException(
                    "AI consultation returned no result"
            );
        }

        // =====================================================
        // 4. VALIDATE AI REPLY
        // =====================================================

        if (aiResult.reply() == null
                || aiResult.reply().isBlank()) {

            throw new IllegalStateException(
                    "AI returned an empty reply"
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

        if (aiResult.recommendedServiceId() != null
                && !aiResult.recommendedServiceId().isBlank()) {

            String recommendedServiceId =
                    aiResult.recommendedServiceId().trim();

            Service recommendedService =
                    serviceRepository
                            .findById(recommendedServiceId)
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "AI returned an invalid serviceId: "
                                                    + recommendedServiceId
                                    )
                            );

            /*
             * AI chỉ được recommend Service đang hoạt động.
             */
            if (!Boolean.TRUE.equals(
                    recommendedService.getIsActive()
            )) {

                throw new IllegalStateException(
                        "AI returned an inactive serviceId: "
                                + recommendedServiceId
                );
            }

            consultation.setRecommendedService(
                    recommendedService
            );
        }

        // =====================================================
        // 8. UPDATE CONSULTATION STATUS
        // =====================================================

        /*
         * Không tin hoàn toàn status do LLM trả về.
         *
         * AI hiện chỉ có quyền đưa consultation về:
         *
         * ACTIVE
         * READY_FOR_CONFIRMATION
         *
         * CONFIRMED sẽ do backend xử lý riêng sau khi
         * Customer thực sự xác nhận.
         */
        if (aiResult.requirementStatus()
                == ConsultationStatus.READY_FOR_CONFIRMATION) {

            consultation.setStatus(
                    ConsultationStatus.READY_FOR_CONFIRMATION
            );

        } else {

            consultation.setStatus(
                    ConsultationStatus.ACTIVE
            );
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
                                aiResult.reply().trim()
                        )
                        .build();

        messageRepository.save(assistantMessage);

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

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new IllegalStateException(
                    "User is not authenticated"
            );
        }

        String cognitoSub = authentication.getName();

        if (cognitoSub == null || cognitoSub.isBlank()) {
            throw new IllegalStateException(
                    "Authenticated Cognito sub is empty"
            );
        }

        return userService.findByCognitoSub(
                cognitoSub.trim()
        );
    }
}