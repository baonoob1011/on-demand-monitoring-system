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
            Duration.ofSeconds(12);
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

            if (aiResult.recommendedServiceId() != null
                    && !aiResult.recommendedServiceId().isBlank()) {

                String recommendedServiceId =
                        aiResult.recommendedServiceId().trim();

                Optional<Service> recommendedServiceOptional =
                        serviceRepository.findById(recommendedServiceId);

                if (recommendedServiceOptional.isEmpty()) {
                    log.warn(
                            "AI returned invalid serviceId. consultationId={}, serviceId={}",
                            consultationId,
                            recommendedServiceId
                    );
                } else {
                    Service recommendedService =
                            recommendedServiceOptional.get();

                    /*
                     * AI chỉ được recommend Service đang hoạt động.
                     */
                    if (Boolean.TRUE.equals(
                            recommendedService.getIsActive()
                    )) {

                        consultation.setRecommendedService(
                                recommendedService
                        );
                        saveLearningEntry(consultation, customer, request.getMessage(), recommendedService);

                    } else {
                        log.warn(
                                "AI returned inactive serviceId. consultationId={}, serviceId={}",
                                consultationId,
                                recommendedServiceId
                        );
                    }
                }
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
                                Hãy dùng thông tin này cho RAG và tư vấn, nhưng không hỏi lại nếu đã đủ rõ.

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
        applyFallbackRecommendedService(consultation, aiHistory);
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

        String text = normalizeHistory(aiHistory);
        String latestCustomerText = normalizeLatestCustomerMessage(aiHistory);
        boolean latestLogistics = containsAny(
                latestCustomerText,
                "kho bai",
                "logistics",
                "container",
                "bai xe",
                "tap ket vat tu",
                "kiem ke"
        );
        boolean latestIndustrial = containsAny(
                latestCustomerText,
                "nha may",
                "khu cong nghiep",
                "bon chua",
                "khu nguy hiem",
                "tai san ngoai troi"
        );
        boolean latestEventCrowd = containsAny(
                latestCustomerText,
                "su kien",
                "dong nguoi",
                "dam dong",
                "le hoi",
                "san van dong",
                "bai do xe"
        );
        boolean latestAgriculture = containsAny(
                latestCustomerText,
                "nong nghiep",
                "cay trong",
                "ca phe",
                "thanh long",
                "vuon thanh long",
                "lua",
                "vuon",
                "thieu nuoc",
                "sau benh",
                "sinh truong"
        );
        boolean building = containsAny(
                text,
                "toa nha",
                "cong trinh",
                "co so ha tang",
                "mai",
                "mat dung",
                "cong ra vao"
        );
        boolean agriculture = latestAgriculture || containsAny(
                text,
                "nong nghiep",
                "cay trong",
                "ca phe",
                "thanh long",
                "vuon thanh long",
                "lua",
                "vuon",
                "thieu nuoc",
                "sau benh",
                "sinh truong"
        );
        boolean environment = containsAny(
                text,
                "moi truong",
                "ngap",
                "sat lo",
                "xoi mon",
                "o nhiem",
                "nuoc thai",
                "song",
                "kenh"
        );
        boolean fire = containsAny(
                text,
                "chay rung",
                "khoi",
                "diem nhiet",
                "nguy co chay"
        );
        boolean security = containsAny(
                text,
                "an ninh",
                "xam nhap",
                "tuan tra",
                "cong ra vao",
                "hang rao",
                "kho bai"
        );
        boolean traffic = containsAny(
                text,
                "giao thong",
                "un tac",
                "tai nan",
                "luu luong",
                "diem nghen"
        );
        boolean solar = containsAny(
                text,
                "tam pin",
                "nang luong mat troi",
                "solar",
                "inverter"
        );
        boolean powerLine = containsAny(
                text,
                "duong day dien",
                "tram bien ap",
                "cot dien",
                "hanh lang an toan"
        );
        boolean mapping = containsAny(
                text,
                "ban do",
                "2d",
                "3d",
                "orthomosaic",
                "point cloud",
                "do dac"
        );
        boolean industrial = latestIndustrial || containsAny(
                text,
                "nha may",
                "khu cong nghiep",
                "bon chua",
                "khu nguy hiem",
                "tai san ngoai troi"
        );
        boolean logistics = latestLogistics || containsAny(
                text,
                "kho bai",
                "logistics",
                "container",
                "bai xe",
                "tap ket vat tu",
                "kiem ke"
        );
        boolean eventCrowd = latestEventCrowd || containsAny(
                text,
                "su kien",
                "dong nguoi",
                "dam dong",
                "le hoi",
                "san van dong",
                "bai do xe"
        );
        boolean pipeline = containsAny(
                text,
                "duong ong",
                "ro ri",
                "hanh lang tuyen",
                "tuyen ong"
        );
        boolean bridgeRoad = containsAny(
                text,
                "cau",
                "duong",
                "mat duong",
                "sut lun",
                "o ga",
                "taluy"
        );
        boolean crack = containsAny(
                text,
                "nut vo",
                "hu hong",
                "xuong cap",
                "ket cau"
        );
        boolean hotSpot = containsAny(
                text,
                "diem nong",
                "nhiet",
                "thermal",
                "qua nhiet"
        );
        boolean safety = containsAny(
                text,
                "an toan",
                "xam nhap",
                "nguy hiem"
        );
        boolean progress = containsAny(
                text,
                "tien do",
                "thi cong",
                "dinh ky",
                "hang tuan",
                "hang thang"
        );
        boolean priorityArea = containsAny(
                text,
                "mai",
                "mat dung",
                "cong ra vao",
                "toan bo",
                "mat tien",
                "tang",
                "khu vuc"
        );
        boolean expectedOutcome = containsAny(
                text,
                "bao cao",
                "ban do",
                "anh",
                "video",
                "bang chung",
                "danh dau",
                "toa do"
        );
        boolean notification = containsAny(
                text,
                "email",
                "tin nhan",
                "thong bao",
                "zalo",
                "sms"
        );

        if (latestLogistics || latestIndustrial || latestEventCrowd) {
            agriculture = false;
        }
        if (latestAgriculture) {
            building = false;
        }

        if (!building) {
            return buildNonBuildingFallbackReply(
                    agriculture,
                    environment,
                    fire,
                    security,
                    traffic,
                    solar,
                    powerLine,
                    mapping,
                    industrial,
                    logistics,
                    eventCrowd,
                    pipeline,
                    bridgeRoad,
                    priorityArea,
                    expectedOutcome,
                    notification,
                    progress
            );
        }

        if (!crack && !hotSpot && !safety && !progress) {
            return promptTemplateService.getRequired("FALLBACK_BUILDING_DELIVERABLE");
        }

        if (!priorityArea) {
            return promptTemplateService.render(
                    "FALLBACK_BUILDING_PRIORITY",
                    Map.of("optionalGoal", buildOptionalProblemPhrase(crack, hotSpot, safety, progress))
            );
        }

        if (!expectedOutcome) {
            return promptTemplateService.getRequired("FALLBACK_BUILDING_OUTCOME");
        }

        if (!notification) {
            return promptTemplateService.getRequired("FALLBACK_BUILDING_NOTIFICATION");
        }

        return promptTemplateService.getRequired("FALLBACK_BUILDING_READY");
    }

    private String buildNonBuildingFallbackReply(
            boolean agriculture,
            boolean environment,
            boolean fire,
            boolean security,
            boolean traffic,
            boolean solar,
            boolean powerLine,
            boolean mapping,
            boolean industrial,
            boolean logistics,
            boolean eventCrowd,
            boolean pipeline,
            boolean bridgeRoad,
            boolean priorityArea,
            boolean expectedOutcome,
            boolean notification,
            boolean frequency
    ) {

        if (industrial) {
            return promptTemplateService.getRequired("FALLBACK_INDUSTRIAL");
        }

        if (logistics) {
            return promptTemplateService.getRequired("FALLBACK_LOGISTICS");
        }

        if (eventCrowd) {
            return promptTemplateService.getRequired("FALLBACK_EVENT_CROWD");
        }

        if (agriculture) {
            if (!priorityArea) {
                return promptTemplateService.getRequired("FALLBACK_AGRICULTURE_PRIORITY");
            }
            if (!frequency) {
                return promptTemplateService.getRequired("FALLBACK_AGRICULTURE_FREQUENCY");
            }
        }

        if (environment) {
            if (!priorityArea) {
                return promptTemplateService.getRequired("FALLBACK_ENVIRONMENT");
            }
        }

        if (fire) {
            if (!notification) {
                return promptTemplateService.getRequired("FALLBACK_FIRE");
            }
        }

        if (security) {
            if (!frequency) {
                return promptTemplateService.getRequired("FALLBACK_SECURITY");
            }
        }

        if (traffic) {
            return promptTemplateService.getRequired("FALLBACK_TRAFFIC");
        }

        if (solar) {
            return promptTemplateService.getRequired("FALLBACK_SOLAR");
        }

        if (powerLine) {
            return promptTemplateService.getRequired("FALLBACK_POWER_LINE");
        }

        if (mapping) {
            return promptTemplateService.getRequired("FALLBACK_MAPPING");
        }

        if (pipeline) {
            return promptTemplateService.getRequired("FALLBACK_PIPELINE");
        }

        if (bridgeRoad) {
            return promptTemplateService.getRequired("FALLBACK_BRIDGE_ROAD");
        }

        if (!expectedOutcome) {
            return promptTemplateService.getRequired("FALLBACK_GENERIC_MISSING");
        }

        return promptTemplateService.getRequired("FALLBACK_GENERIC_RECEIVE_RESULT");
    }

    private void applyFallbackRecommendedService(
            CustomerConsultation consultation,
            List<ConsultationMessage> aiHistory
    ) {

        if (consultation.getRecommendedService() != null) {
            return;
        }

        String text = normalizeHistory(aiHistory);
        String latestCustomerText = normalizeLatestCustomerMessage(aiHistory);

        serviceRepository.findAll()
                .stream()
                .filter(service -> Boolean.TRUE.equals(service.getIsActive()))
                .filter(service -> {
                    String name = normalizeText(service.getName());
                    if (containsAny(latestCustomerText, "nong nghiep", "cay trong", "ca phe", "thanh long", "vuon", "lua", "sau benh", "thieu nuoc")) {
                        return name.contains("nong nghiep")
                                || name.contains("cay trong")
                                || name.contains("ndvi")
                                || name.contains("thuc vat");
                    }
                    if (containsAny(latestCustomerText, "kho bai", "logistics", "container", "bai xe")) {
                        return name.contains("kho bai")
                                || name.contains("logistics")
                                || name.contains("container");
                    }
                    if (containsAny(latestCustomerText, "nha may", "khu cong nghiep")) {
                        return name.contains("nha may")
                                || name.contains("khu cong nghiep");
                    }
                    if (containsAny(latestCustomerText, "su kien", "dong nguoi", "dam dong")) {
                        return name.contains("su kien")
                                || name.contains("dong nguoi");
                    }
                    if (containsAny(text, "toa nha", "cong trinh", "co so ha tang")) {
                        return name.contains("toa nha")
                            || name.contains("co so ha tang")
                            || name.contains("infrastructure")
                            || name.contains("building");
                    }
                    return false;
                })
                .findFirst()
                .ifPresent(consultation::setRecommendedService);
    }

    private String buildFallbackRequirementSummary(
            List<ConsultationMessage> aiHistory
    ) {

        String text = normalizeHistory(aiHistory);
        String latestCustomerText = normalizeLatestCustomerMessage(aiHistory);
        boolean agriculture = containsAny(latestCustomerText, "nong nghiep", "cay trong", "ca phe", "thanh long", "vuon", "lua", "sau benh", "thieu nuoc");
        boolean building = !agriculture && containsAny(text, "toa nha", "cong trinh", "co so ha tang");
        boolean cropIssue = containsAny(text, "sau benh", "thieu nuoc", "sinh truong", "vang la", "kho heo", "bat thuong");
        boolean crack = containsAny(text, "nut vo", "hu hong", "xuong cap", "ket cau");
        boolean hotSpot = containsAny(text, "diem nong", "nhiet", "thermal", "qua nhiet");
        boolean safety = containsAny(text, "an toan", "xam nhap", "nguy hiem");
        boolean progress = containsAny(text, "tien do", "thi cong", "dinh ky", "hang tuan", "hang thang");

        if (building) {
            return "Giám sát tòa nhà/công trình. "
                    + buildSummaryProblemSentence(crack, hotSpot, safety, progress)
                    + "Cần làm rõ khu vực ưu tiên và kết quả bàn giao ảnh/video hoặc báo cáo kèm hình.";
        }
        if (agriculture) {
            return "Giám sát vườn/cây trồng. "
                    + (cropIssue ? "Có yêu cầu phân tích thêm về sâu bệnh/thiếu nước/sinh trưởng. " : "")
                    + "Cần làm rõ khu vực cần bay chụp, tần suất và kết quả bàn giao mong muốn.";
        }

        return "Tạo yêu cầu giám sát. Cần làm rõ đối tượng/khu vực cần bay chụp, phạm vi và kết quả bàn giao mong muốn.";
    }

    private String buildOptionalProblemPhrase(
            boolean crack,
            boolean hotSpot,
            boolean safety,
            boolean progress
    ) {
        String phrase = buildProblemPhrase(crack, hotSpot, safety, progress);
        if ("ghi nhận hình ảnh/video hiện trạng".equals(phrase)) {
            return "";
        }
        return " với mục tiêu phụ là " + phrase;
    }

    private String buildSummaryProblemSentence(
            boolean crack,
            boolean hotSpot,
            boolean safety,
            boolean progress
    ) {
        String phrase = buildProblemPhrase(crack, hotSpot, safety, progress);
        if ("ghi nhận hình ảnh/video hiện trạng".equals(phrase)) {
            return "";
        }
        return "Mục tiêu phân tích thêm: " + phrase + ". ";
    }

    private String buildProblemPhrase(
            boolean crack,
            boolean hotSpot,
            boolean safety,
            boolean progress
    ) {

        List<String> problems = new java.util.ArrayList<>();
        if (crack) {
            problems.add("kiểm tra nứt vỡ/hư hỏng");
        }
        if (hotSpot) {
            problems.add("phát hiện điểm nóng");
        }
        if (safety) {
            problems.add("rà soát an toàn khu vực");
        }
        if (progress) {
            problems.add("theo dõi tiến độ");
        }

        if (problems.isEmpty()) {
            return "ghi nhận hình ảnh/video hiện trạng";
        }

        return String.join(", ", problems);
    }

    private boolean containsAny(String text, String... needles) {

        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }

        return false;
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
