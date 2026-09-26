package com.ondemandmonitoring.Consultation.config;

import com.ondemandmonitoring.Consultation.domains.ConsultationPromptTemplate;
import com.ondemandmonitoring.Consultation.repositories.ConsultationPromptTemplateRepository;
import com.ondemandmonitoring.Consultation.services.ConsultationPromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConsultationPromptTemplateInitializer implements CommandLineRunner {

    private final ConsultationPromptTemplateRepository repository;

    @Override
    @Transactional
    public void run(String... args) {
        int upserted = 0;
        upserted += upsert(ConsultationPromptTemplateService.AI_SYSTEM_PROMPT, """
                Bạn là trợ lý tư vấn request giám sát của On-Demand Monitoring System.

                Nguyên tắc chính:
                - Nền tảng chủ yếu nhận request bay chụp và bàn giao ảnh/video/báo cáo kèm hình.
                - AI detect/phân tích chỉ là yêu cầu phụ thêm khi khách hàng nói rõ hoặc chọn thêm.
                - Không mặc định khách cần phát hiện bất thường, điểm nóng, nứt vỡ hoặc cảnh báo.
                - Hỏi từ nhỏ tới lớn: đối tượng/khu vực cần bay chụp -> kết quả bàn giao ảnh/video/báo cáo -> phạm vi/khu vực ưu tiên -> AI detect phụ thêm nếu có -> tần suất/cách nhận kết quả.
                - Mỗi lượt chỉ hỏi 1 đến 2 ý quan trọng nhất.
                - Không hỏi lại thông tin khách đã cung cấp.
                - Không tự bịa dịch vụ, serviceId, khả năng kỹ thuật hoặc requirement.
                - Chỉ đề xuất service xuất hiện trong AVAILABLE SERVICES và sao chép đúng serviceId.
                - requirementSummary chỉ ghi những gì khách đã nói rõ.
                - Chỉ dùng status ACTIVE hoặc READY_FOR_CONFIRMATION.

                READY_FOR_CONFIRMATION chỉ khi đã rõ:
                1. Đối tượng/khu vực cần bay chụp.
                2. Kết quả bàn giao cần nhận.
                3. Service phù hợp từ AVAILABLE SERVICES.
                4. Requirement summary ngắn gọn, không suy diễn.

                Trả về structured output gồm: reply, requirementStatus, recommendedServiceId, requirementSummary, requirements.
                Mặc định trả lời tiếng Việt tự nhiên, ngắn gọn, chuyên nghiệp.
                """);
        upserted += upsert(ConsultationPromptTemplateService.AI_USER_TASK_PROMPT, """
                CUỘC HỘI THOẠI HIỆN TẠI

                {conversationContext}

                CÁC DỊCH VỤ ĐƯỢC TRUY XUẤT TỪ RAG

                {knowledgeContext}

                NHIỆM VỤ

                Tiếp tục tư vấn khách hàng theo nguyên tắc trong system prompt.
                Phân tích toàn bộ hội thoại để xác định thông tin đã biết, thông tin còn thiếu và service phù hợp trong AVAILABLE SERVICES.

                Nếu còn thiếu thông tin quan trọng:
                - status ACTIVE.
                - Hỏi 1 đến 2 câu tiếp theo.
                - Ưu tiên hỏi về bàn giao ảnh/video/báo cáo trước khi hỏi AI detect.

                Nếu đã đủ thông tin:
                - Chọn service phù hợp từ RAG.
                - Dùng đúng serviceId.
                - Tóm tắt requirement đúng dữ liệu khách đã nói.
                - status READY_FOR_CONFIRMATION.

                Tuyệt đối không tự thêm mục tiêu phát hiện bất thường, điểm nóng, nứt vỡ hoặc cảnh báo nếu khách chưa yêu cầu.
                """);

        for (Map.Entry<String, String> entry : fallbackTemplates().entrySet()) {
            upserted += upsert(entry.getKey(), entry.getValue());
        }

        log.info("Consultation prompt templates seed completed: upserted={}", upserted);
    }

    private Map<String, String> fallbackTemplates() {
        return Map.ofEntries(
                Map.entry("FALLBACK_BUILDING_DELIVERABLE", """
                        Mình đã ghi nhận nhu cầu giám sát tòa nhà/công trình.

                        Anh/chị muốn bàn giao kết quả dạng ảnh, video, hay báo cáo kèm hình ảnh? Nếu cần AI phân tích thêm thì mình sẽ ghi thêm mục tiêu đó.
                        """),
                Map.entry("FALLBACK_BUILDING_PRIORITY", """
                        Mình đã ghi nhận yêu cầu giám sát công trình{optionalGoal}.

                        Anh/chị muốn ưu tiên khu vực nào: mái, mặt đứng, mặt tiền, cổng ra vào, một tầng/khu cụ thể, hay toàn bộ công trình?
                        """),
                Map.entry("FALLBACK_BUILDING_OUTCOME", """
                        Mình đã rõ đối tượng và khu vực ưu tiên.

                        Kết quả bàn giao anh/chị muốn nhận là ảnh, video, báo cáo kèm hình, hay bản đồ đánh dấu vị trí?
                        """),
                Map.entry("FALLBACK_BUILDING_NOTIFICATION", """
                        Request đã khá rõ: giám sát tòa nhà/công trình, có khu vực ưu tiên và kết quả bàn giao mong muốn.

                        Anh/chị muốn nhận kết quả sau chuyến bay trong báo cáo, hay cần thông báo nhanh qua email/tin nhắn khi có phân tích AI phụ thêm?
                        """),
                Map.entry("FALLBACK_BUILDING_READY", """
                        Mình đã có đủ thông tin chính để lập request giám sát công trình.

                        Tóm tắt: giám sát tòa nhà/công trình; ưu tiên khu vực đã nêu; kết quả bàn giao gồm ảnh/video hoặc báo cáo kèm hình theo yêu cầu. Anh/chị kiểm tra lại thông tin bên phải, nếu đúng có thể tiếp tục sang bước thời gian và kết quả.
                        """),
                Map.entry("FALLBACK_GENERIC_MISSING", """
                        Mình đang cần làm rõ request giám sát.

                        Anh/chị cho biết đối tượng/khu vực cần bay chụp là gì và muốn nhận kết quả dạng ảnh, video hay báo cáo kèm hình?
                        """),
                Map.entry("FALLBACK_INDUSTRIAL", """
                        Mình đang hiểu nhu cầu là giám sát khu công nghiệp/nhà máy.

                        Anh/chị muốn ưu tiên chụp khu vực nào trước: mái nhà, bồn chứa, hàng rào, tài sản ngoài trời hay lối ra vào? Kết quả cần ảnh tổng quan, video, hay báo cáo kèm hình?
                        """),
                Map.entry("FALLBACK_LOGISTICS", """
                        Mình đang hiểu nhu cầu là giám sát kho bãi/logistics.

                        Anh/chị muốn ưu tiên việc nào: kiểm kê container/xe/vật tư, phát hiện khu vực quá tải, theo dõi luồng ra vào hay kiểm tra an ninh? Kết quả cần ảnh tổng quan, danh sách vị trí bất thường hay báo cáo định kỳ?
                        """),
                Map.entry("FALLBACK_EVENT_CROWD", """
                        Mình đang hiểu nhu cầu là giám sát sự kiện hoặc khu đông người.

                        Anh/chị muốn theo dõi mật độ đám đông, luồng di chuyển, điểm ùn ứ, bãi đỗ xe hay khu vực an ninh? Cần cảnh báo theo thời gian thực hay chỉ báo cáo sau sự kiện?
                        """),
                Map.entry("FALLBACK_AGRICULTURE_PRIORITY", """
                        Mình đang hiểu nhu cầu là giám sát nông nghiệp/cây trồng.

                        Anh/chị muốn bay chụp toàn bộ vườn hay một phần cụ thể? Kết quả cần ảnh/video hiện trạng trước, hay cần thêm AI phân tích cây sinh trưởng kém, thiếu nước hoặc sâu bệnh?
                        """),
                Map.entry("FALLBACK_AGRICULTURE_FREQUENCY", """
                        Mình đã ghi nhận hướng giám sát cây trồng và khu vực ưu tiên.

                        Anh/chị muốn kiểm tra một lần để biết hiện trạng hay theo dõi định kỳ hằng tuần/hằng tháng để so sánh xu hướng?
                        """),
                Map.entry("FALLBACK_GENERIC_RECEIVE_RESULT", """
                        Mình đã ghi nhận nhu cầu giám sát của anh/chị.

                        Để chốt request rõ hơn, anh/chị bổ sung tần suất theo dõi và cách muốn nhận kết quả sau chuyến bay nhé.
                        """),
                Map.entry("FALLBACK_ENVIRONMENT", """
                        Mình đang hiểu nhu cầu là giám sát môi trường/khu vực rủi ro.

                        Anh/chị muốn ưu tiên theo dõi ngập, sạt lở, xói mòn, ô nhiễm nguồn nước, hay điểm bất thường khác? Khu vực ưu tiên là ven sông/kênh, khu dân cư, nhà máy hay toàn bộ vùng?
                        """),
                Map.entry("FALLBACK_FIRE", """
                        Mình đang hiểu nhu cầu là phát hiện cháy rừng/điểm nhiệt.

                        Anh/chị cần cảnh báo gần thời gian thực khi thấy khói/điểm nhiệt, hay chỉ cần bản đồ nguy cơ và báo cáo định kỳ? Kênh nhận cảnh báo là email, SMS/tin nhắn hay dashboard?
                        """),
                Map.entry("FALLBACK_SECURITY", """
                        Mình đang hiểu nhu cầu là giám sát an ninh khu vực.

                        Anh/chị muốn tuần tra một lần, tuần tra theo khung giờ cố định, hay giám sát khi có sự kiện? Cần ưu tiên cổng ra vào, hàng rào, kho bãi hay điểm nhạy cảm nào?
                        """),
                Map.entry("FALLBACK_TRAFFIC", """
                        Mình đang hiểu nhu cầu là giám sát giao thông.

                        Anh/chị muốn theo dõi lưu lượng xe, ùn tắc, tai nạn, điểm nghẽn, hay tình trạng mặt đường? Kết quả cần là video quan sát, thống kê lưu lượng, hay báo cáo điểm bất thường?
                        """),
                Map.entry("FALLBACK_SOLAR", """
                        Mình đang hiểu nhu cầu là kiểm tra tấm pin năng lượng mặt trời.

                        Anh/chị muốn phát hiện điểm nóng, tấm lỗi, bụi bẩn/suy giảm hiệu suất, hay kiểm tra inverter/khu kỹ thuật? Kết quả cần ảnh nhiệt kèm vị trí từng tấm hay báo cáo tổng hợp theo dãy?
                        """),
                Map.entry("FALLBACK_POWER_LINE", """
                        Mình đang hiểu nhu cầu là kiểm tra đường dây điện/trạm biến áp.

                        Anh/chị muốn kiểm tra cột, sứ, dây dẫn, điểm nhiệt thiết bị, hành lang an toàn hay vật cản gần tuyến? Cần báo cáo theo từng vị trí/cột hay tổng hợp toàn tuyến?
                        """),
                Map.entry("FALLBACK_MAPPING", """
                        Mình đang hiểu nhu cầu là khảo sát bản đồ 2D/3D.

                        Anh/chị cần orthomosaic 2D, mô hình 3D, point cloud, đo diện tích/thể tích hay bản đồ hiện trạng? Độ chi tiết mong muốn và phạm vi đo đạc là bao nhiêu?
                        """),
                Map.entry("FALLBACK_PIPELINE", """
                        Mình đang hiểu nhu cầu là kiểm tra đường ống/hành lang tuyến.

                        Anh/chị muốn phát hiện rò rỉ, xâm lấn hành lang, hư hỏng bề mặt, điểm nhiệt hay vật cản trên tuyến? Cần báo cáo theo từng đoạn tuyến hay theo tọa độ điểm bất thường?
                        """),
                Map.entry("FALLBACK_BRIDGE_ROAD", """
                        Mình đang hiểu nhu cầu là kiểm tra cầu/đường/hạ tầng giao thông.

                        Anh/chị muốn phát hiện nứt vỡ, sụt lún, hư hỏng mặt đường, taluy/sạt lở hay điểm nguy hiểm giao thông? Khu vực ưu tiên là mặt cầu, mặt đường, mép taluy hay toàn tuyến?
                        """)
        );
    }

    private int upsert(String key, String content) {
        ConsultationPromptTemplate template = repository.findByTemplateKey(key)
                .orElseGet(() -> ConsultationPromptTemplate.builder()
                        .templateKey(key)
                        .build());
        boolean changed = template.getContent() == null || !template.getContent().equals(content.trim())
                || !Boolean.TRUE.equals(template.getActive());
        template.setContent(content.trim());
        template.setActive(true);
        repository.save(template);
        return changed ? 1 : 0;
    }
}
