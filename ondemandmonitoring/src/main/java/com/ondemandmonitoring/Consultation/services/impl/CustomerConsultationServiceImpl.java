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
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
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
    private final CustomerConsultationMapper consultationMapper;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final AiConsultationService aiConsultationService;
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
            return """
                    Mình đã ghi nhận nhu cầu giám sát tòa nhà/công trình.

                    Mục tiêu chính của lần giám sát này là kiểm tra nứt vỡ/hư hỏng, phát hiện điểm nóng, rà soát an toàn khu vực hay theo dõi tiến độ?
                    """.trim();
        }

        if (!priorityArea) {
            return """
                    Mình đã hiểu mục tiêu giám sát công trình là %s.

                    Anh/chị muốn ưu tiên khu vực nào: mái, mặt đứng, mặt tiền, cổng ra vào, một tầng/khu cụ thể, hay toàn bộ công trình?
                    """.formatted(buildProblemPhrase(crack, hotSpot, safety, progress)).trim();
        }

        if (!expectedOutcome) {
            return """
                    Mình đã rõ: đối tượng là tòa nhà/công trình, mục tiêu là %s, và đã có khu vực ưu tiên.

                    Kết quả anh/chị muốn nhận là ảnh/video minh chứng, báo cáo vị trí nứt vỡ/điểm nóng, bản đồ đánh dấu khu vực bất thường, hay cả hai?
                    """.formatted(buildProblemPhrase(crack, hotSpot, safety, progress)).trim();
        }

        if (!notification) {
            return """
                    Request đã khá rõ: giám sát tòa nhà/công trình để %s, có khu vực ưu tiên và kết quả mong muốn.

                    Khi phát hiện bất thường, anh/chị muốn được thông báo qua email, tin nhắn/SMS, hay chỉ tổng hợp trong báo cáo sau chuyến bay?
                    """.formatted(buildProblemPhrase(crack, hotSpot, safety, progress)).trim();
        }

        return """
                Mình đã có đủ thông tin chính để lập request giám sát công trình.

                Tóm tắt: giám sát tòa nhà/công trình để %s; ưu tiên khu vực đã nêu; kết quả gồm minh chứng/báo cáo bất thường; thông báo theo kênh anh/chị đã chọn. Anh/chị kiểm tra lại thông tin bên phải, nếu đúng có thể tiếp tục sang bước thời gian và kết quả.
                """.formatted(buildProblemPhrase(crack, hotSpot, safety, progress)).trim();
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
            return """
                    Mình đang hiểu nhu cầu là giám sát khu công nghiệp/nhà máy.

                    Anh/chị muốn kiểm tra mái nhà, bồn chứa, khu vực nguy hiểm, hàng rào, tài sản ngoài trời hay lối ra vào? Mức ưu tiên là phát hiện hư hỏng, điểm nóng, rò rỉ, xâm nhập hay kiểm kê hiện trạng?
                    """.trim();
        }

        if (logistics) {
            return """
                    Mình đang hiểu nhu cầu là giám sát kho bãi/logistics.

                    Anh/chị muốn ưu tiên việc nào: kiểm kê container/xe/vật tư, phát hiện khu vực quá tải, theo dõi luồng ra vào hay kiểm tra an ninh? Kết quả cần ảnh tổng quan, danh sách vị trí bất thường hay báo cáo định kỳ?
                    """.trim();
        }

        if (eventCrowd) {
            return """
                    Mình đang hiểu nhu cầu là giám sát sự kiện hoặc khu đông người.

                    Anh/chị muốn theo dõi mật độ đám đông, luồng di chuyển, điểm ùn ứ, bãi đỗ xe hay khu vực an ninh? Cần cảnh báo theo thời gian thực hay chỉ báo cáo sau sự kiện?
                    """.trim();
        }

        if (agriculture) {
            if (!priorityArea) {
                return """
                        Mình đang hiểu nhu cầu là giám sát nông nghiệp/cây trồng.

                        Anh/chị muốn ưu tiên phát hiện vấn đề nào trước: cây sinh trưởng kém, thiếu nước, sâu bệnh, khu vực chết cây, hay thay đổi bất thường theo thời gian? Khu vực cần theo dõi là toàn bộ vườn hay một phần cụ thể?
                        """.trim();
            }
            if (!frequency) {
                return """
                        Mình đã ghi nhận hướng giám sát cây trồng và khu vực ưu tiên.

                        Anh/chị muốn kiểm tra một lần để biết hiện trạng hay theo dõi định kỳ hằng tuần/hằng tháng để so sánh xu hướng?
                        """.trim();
            }
        }

        if (environment) {
            if (!priorityArea) {
                return """
                        Mình đang hiểu nhu cầu là giám sát môi trường/khu vực rủi ro.

                        Anh/chị muốn ưu tiên theo dõi ngập, sạt lở, xói mòn, ô nhiễm nguồn nước, hay điểm bất thường khác? Khu vực ưu tiên là ven sông/kênh, khu dân cư, nhà máy hay toàn bộ vùng?
                        """.trim();
            }
        }

        if (fire) {
            if (!notification) {
                return """
                        Mình đang hiểu nhu cầu là phát hiện cháy rừng/điểm nhiệt.

                        Anh/chị cần cảnh báo gần thời gian thực khi thấy khói/điểm nhiệt, hay chỉ cần bản đồ nguy cơ và báo cáo định kỳ? Kênh nhận cảnh báo là email, SMS/tin nhắn hay dashboard?
                        """.trim();
            }
        }

        if (security) {
            if (!frequency) {
                return """
                        Mình đang hiểu nhu cầu là giám sát an ninh khu vực.

                        Anh/chị muốn tuần tra một lần, tuần tra theo khung giờ cố định, hay giám sát khi có sự kiện? Cần ưu tiên cổng ra vào, hàng rào, kho bãi hay điểm nhạy cảm nào?
                        """.trim();
            }
        }

        if (traffic) {
            return """
                    Mình đang hiểu nhu cầu là giám sát giao thông.

                    Anh/chị muốn theo dõi lưu lượng xe, ùn tắc, tai nạn, điểm nghẽn, hay tình trạng mặt đường? Kết quả cần là video quan sát, thống kê lưu lượng, hay báo cáo điểm bất thường?
                    """.trim();
        }

        if (solar) {
            return """
                    Mình đang hiểu nhu cầu là kiểm tra tấm pin năng lượng mặt trời.

                    Anh/chị muốn phát hiện điểm nóng, tấm lỗi, bụi bẩn/suy giảm hiệu suất, hay kiểm tra inverter/khu kỹ thuật? Kết quả cần ảnh nhiệt kèm vị trí từng tấm hay báo cáo tổng hợp theo dãy?
                    """.trim();
        }

        if (powerLine) {
            return """
                    Mình đang hiểu nhu cầu là kiểm tra đường dây điện/trạm biến áp.

                    Anh/chị muốn kiểm tra cột, sứ, dây dẫn, điểm nhiệt thiết bị, hành lang an toàn hay vật cản gần tuyến? Cần báo cáo theo từng vị trí/cột hay tổng hợp toàn tuyến?
                    """.trim();
        }

        if (mapping) {
            return """
                    Mình đang hiểu nhu cầu là khảo sát bản đồ 2D/3D.

                    Anh/chị cần orthomosaic 2D, mô hình 3D, point cloud, đo diện tích/thể tích hay bản đồ hiện trạng? Độ chi tiết mong muốn và phạm vi đo đạc là bao nhiêu?
                    """.trim();
        }

        if (pipeline) {
            return """
                    Mình đang hiểu nhu cầu là kiểm tra đường ống/hành lang tuyến.

                    Anh/chị muốn phát hiện rò rỉ, xâm lấn hành lang, hư hỏng bề mặt, điểm nhiệt hay vật cản trên tuyến? Cần báo cáo theo từng đoạn tuyến hay theo tọa độ điểm bất thường?
                    """.trim();
        }

        if (bridgeRoad) {
            return """
                    Mình đang hiểu nhu cầu là kiểm tra cầu/đường/hạ tầng giao thông.

                    Anh/chị muốn phát hiện nứt vỡ, sụt lún, hư hỏng mặt đường, taluy/sạt lở hay điểm nguy hiểm giao thông? Khu vực ưu tiên là mặt cầu, mặt đường, mép taluy hay toàn tuyến?
                    """.trim();
        }

        if (!expectedOutcome) {
            return """
                    Mình đang cần làm rõ request giám sát.

                    Anh/chị cho biết đối tượng cần giám sát là gì, vấn đề chính muốn phát hiện, khu vực cần ưu tiên và kết quả mong muốn sau chuyến bay?
                    """.trim();
        }

        return """
                Mình đã ghi nhận nhu cầu giám sát của anh/chị.

                Để chốt request rõ hơn, anh/chị bổ sung tần suất theo dõi, mức độ khẩn cấp và cách muốn nhận thông báo khi phát hiện bất thường nhé.
                """.trim();
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
        boolean building = containsAny(text, "toa nha", "cong trinh", "co so ha tang");
        boolean crack = containsAny(text, "nut vo", "hu hong", "xuong cap", "ket cau");
        boolean hotSpot = containsAny(text, "diem nong", "nhiet", "thermal", "qua nhiet");
        boolean safety = containsAny(text, "an toan", "xam nhap", "nguy hiem");
        boolean progress = containsAny(text, "tien do", "thi cong", "dinh ky", "hang tuan", "hang thang");

        if (building) {
            return "Khách hàng muốn giám sát tòa nhà/công trình để "
                    + buildProblemPhrase(crack, hotSpot, safety, progress)
                    + ". Cần làm rõ thêm khu vực ưu tiên, kết quả bàn giao và cách thông báo khi phát hiện bất thường.";
        }

        return "Khách hàng muốn tạo yêu cầu giám sát nhưng cần làm rõ đối tượng, mục tiêu, phạm vi, kết quả mong muốn và cách nhận thông báo.";
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
            return "làm rõ tình trạng bất thường";
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
