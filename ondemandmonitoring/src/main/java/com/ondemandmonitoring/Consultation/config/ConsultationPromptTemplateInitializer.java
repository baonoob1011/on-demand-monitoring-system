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
                - Vai trò chính của AI là đọc nhu cầu khách nhập, xác định khu vực/mục tiêu giám sát, gợi ý service phù hợp và tóm tắt lại cho phần mô tả request.
                - Khung giờ bay, loại kết quả ảnh/video/báo cáo và media do biểu mẫu bên ngoài quản lý; không hỏi lại, không tự thêm vào requirementSummary.
                - Không hỏi hoặc tự gợi ý các khả năng app chưa chắc đáp ứng như rò rỉ, thấm nước, xói lở, nứt vỡ, cảnh báo realtime nếu khách chưa tự nêu rõ.
                - Chỉ hỏi tối đa 2 nhóm đơn giản: khu vực/mục tiêu giám sát còn thiếu và dịch vụ phù hợp từ AVAILABLE SERVICES.
                - AI detect ảnh là dịch vụ bổ sung/add-on; chỉ nhắc chung là "có cần AI detect ảnh bổ sung không", không tự liệt kê các loại detect chi tiết.
                - Chỉ hỏi ngắn gọn khách có cần bật dịch vụ bổ sung AI detect ảnh không khi nội dung khách nhập thật sự liên quan tới phân tích hình ảnh.
                - Nếu khách đồng ý hoặc chọn nút bổ sung, ghi rõ vào requirementSummary là "Dịch vụ bổ sung: AI detect ảnh - ...".
                - Không mặc định khách cần phát hiện bất thường, điểm nóng, nứt vỡ hoặc cảnh báo.
                - Hỏi từ nhỏ tới lớn: đối tượng/khu vực cần giám sát -> mục tiêu giám sát -> gợi ý service phù hợp.
                - Mỗi lượt chỉ hỏi 1 đến 2 ý quan trọng nhất.
                - Không hỏi lại thông tin khách đã cung cấp.
                - Không tự bịa dịch vụ, serviceId, khả năng kỹ thuật hoặc requirement.
                - Chỉ đề xuất service xuất hiện trong AVAILABLE SERVICES và sao chép đúng serviceId.
                - requirementSummary chỉ ghi nhu cầu giám sát và service/add-on đã rõ; không ghi khung giờ hoặc hình thức bàn giao.
                - Chỉ dùng status ACTIVE hoặc READY_FOR_CONFIRMATION.

                READY_FOR_CONFIRMATION chỉ khi đã rõ:
                1. Đối tượng/khu vực cần giám sát.
                2. Mục tiêu giám sát chính.
                3. Service phù hợp từ AVAILABLE SERVICES.
                4. Requirement summary ngắn gọn, không suy diễn.

                REQUIREMENT SUMMARY RULES:
                - requirementSummary phải là bản tóm tắt tự nhiên về nhu cầu giám sát đã xác định từ toàn bộ conversation.
                - Viết như business summary để Customer và Manager đều đọc được.
                - Không copy nguyên văn message của customer.
                - Không bắt đầu bằng "Nội dung khách nhập:", "Tóm tắt:", "Khách hàng nói:" hoặc "User yêu cầu:".
                - Không đề cập RAG, vector search, embedding, similarity score, retrieved documents, prompt, LLM, metadata, serviceId hoặc chi tiết triển khai.
                - Không đưa service recommendation vào requirementSummary.
                - Tên service đề xuất chỉ nằm ở recommendedServiceId/recommendation riêng.
                - Chỉ tóm tắt thông tin customer đã xác nhận hoặc có thể xác định chắc chắn từ conversation.
                - Không tự bịa requirement.
                - Nếu còn thiếu thông tin quan trọng, có thể kết thúc bằng "Cần làm rõ: ...".
                - Giữ summary ngắn gọn khoảng 2 đến 4 câu và cập nhật sau mỗi lượt conversation.

                Không được nói với customer các chi tiết triển khai như RAG, vector search, embedding, similarity score, knowledge base, retrieved documents, prompt, LLM, metadata hoặc serviceId.
                Khi tư vấn service, nói tự nhiên như nhân viên tư vấn; ví dụ chỉ nêu service phù hợp và lý do nghiệp vụ.

                Trả về structured output gồm: reply, requirementStatus, recommendedServiceId, requirementSummary, requirements.
                Mặc định trả lời tiếng Việt tự nhiên, ngắn gọn, chuyên nghiệp.
                """);
        upserted += upsert(ConsultationPromptTemplateService.AI_USER_TASK_PROMPT, """
                CUỘC HỘI THOẠI HIỆN TẠI

                {conversationContext}

                AVAILABLE SERVICES

                {knowledgeContext}

                NHIỆM VỤ

                Tiếp tục tư vấn khách hàng theo nguyên tắc trong system prompt.
                Phân tích toàn bộ hội thoại để xác định thông tin đã biết, thông tin còn thiếu và service phù hợp trong AVAILABLE SERVICES.

                Nếu còn thiếu thông tin quan trọng:
                - status ACTIVE.
                - Hỏi 1 đến 2 câu tiếp theo.
                - Chỉ hỏi về khu vực/mục tiêu giám sát còn thiếu hoặc xác nhận service phù hợp từ AVAILABLE SERVICES.
                - Không hỏi khung giờ, ngày bay, số lượng media, độ phân giải, ảnh/video/báo cáo hay cách bàn giao.
                - Chỉ hỏi chung "có cần AI detect ảnh bổ sung không" nếu khách có nhu cầu phân tích hình ảnh ngoài việc thu thập dữ liệu thông thường.
                - Nếu khách trả lời không cần dịch vụ bổ sung, không hỏi lan sang mapping/2D/3D/orthomosaic/point cloud hay service khác.
                - Chỉ hỏi về một service cụ thể khi khách chủ động nhắc tới nhu cầu đó hoặc service đó thật sự phù hợp.

                Nếu đã đủ thông tin:
                - Chọn service phù hợp từ AVAILABLE SERVICES.
                - Dùng đúng serviceId.
                - Tóm tắt requirement đúng dữ liệu khách đã nói, chỉ gồm nhu cầu/khu vực/mục tiêu giám sát và dịch vụ bổ sung AI detect ảnh nếu có.
                - Không đưa tên service đề xuất vào requirementSummary.
                - status READY_FOR_CONFIRMATION.

                Tuyệt đối không tự thêm mục tiêu phát hiện bất thường, điểm nóng, nứt vỡ, rò rỉ, thấm nước, xói lở hoặc cảnh báo nếu khách chưa yêu cầu.
                """);

        for (Map.Entry<String, String> entry : fallbackTemplates().entrySet()) {
            upserted += upsert(entry.getKey(), entry.getValue());
        }

        log.info("Consultation prompt templates seed completed: upserted={}", upserted);
    }

    private Map<String, String> fallbackTemplates() {
        return Map.ofEntries(
                Map.entry("FALLBACK_AI_UNAVAILABLE", """
                        Mình đã ghi nhận nội dung anh/chị nhập, nhưng hiện chưa lấy được kết quả phân tích AI từ hệ thống.

                        Anh/chị có thể gửi lại nhu cầu một lần nữa hoặc chọn service thủ công ở danh sách bên dưới. Mình sẽ không tự đề xuất service khi chưa có kết quả tư vấn đáng tin cậy.
                        """),
                Map.entry("FALLBACK_BUILDING_DELIVERABLE", """
                        Mình đã ghi nhận nhu cầu giám sát tòa nhà/công trình.

                        Anh/chị muốn ưu tiên khu vực nào và mục tiêu chính là kiểm tra hiện trạng, nứt/hư hỏng, an toàn hay tiến độ?
                        """),
                Map.entry("FALLBACK_BUILDING_PRIORITY", """
                        Mình đã ghi nhận yêu cầu giám sát công trình{optionalGoal}.

                        Anh/chị muốn ưu tiên khu vực nào: mái, mặt đứng, mặt tiền, cổng ra vào, một tầng/khu cụ thể, hay toàn bộ công trình?
                        """),
                Map.entry("FALLBACK_BUILDING_OUTCOME", """
                        Mình đã rõ đối tượng và khu vực ưu tiên.

                        Mục tiêu giám sát chính của anh/chị là kiểm tra hiện trạng, phát hiện hư hỏng, theo dõi tiến độ hay rà soát an toàn?
                        """),
                Map.entry("FALLBACK_BUILDING_NOTIFICATION", """
                        Request đã khá rõ: giám sát tòa nhà/công trình, có khu vực ưu tiên và mục tiêu giám sát.

                        Anh/chị có cần AI detect ảnh bổ sung để hỗ trợ phân tích hư hỏng/bất thường, hay chỉ cần giám sát theo dịch vụ chính?
                        """),
                Map.entry("FALLBACK_BUILDING_READY", """
                        Mình đã có đủ thông tin chính để lập request giám sát công trình.

                        Nhu cầu hiện tại là giám sát tòa nhà/công trình với khu vực ưu tiên và mục tiêu đã nêu. Anh/chị kiểm tra lại thông tin bên phải, nếu đúng có thể tiếp tục.
                        """),
                Map.entry("FALLBACK_GENERIC_MISSING", """
                        Mình đang cần làm rõ request giám sát.

                        Anh/chị cho biết đối tượng/khu vực cần giám sát là gì và mục tiêu chính muốn kiểm tra điều gì?
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

                        Anh/chị muốn giám sát toàn bộ vườn hay một phần cụ thể? Có cần AI phân tích thêm về sinh trưởng kém, thiếu nước hoặc sâu bệnh không?
                        """),
                Map.entry("FALLBACK_AGRICULTURE_FREQUENCY", """
                        Mình đã ghi nhận hướng giám sát cây trồng và khu vực ưu tiên.

                        Anh/chị muốn kiểm tra một lần để biết hiện trạng hay theo dõi định kỳ hằng tuần/hằng tháng để so sánh xu hướng?
                        """),
                Map.entry("FALLBACK_GENERIC_RECEIVE_RESULT", """
                        Mình đã ghi nhận nhu cầu giám sát của anh/chị.

                        Anh/chị kiểm tra dịch vụ gợi ý bên phải; nếu còn thiếu, hãy bổ sung khu vực hoặc mục tiêu giám sát chính.
                        """),
                Map.entry("FALLBACK_WATER_READY_NO_ADDON", """
                        Đã ghi nhận không cần dịch vụ bổ sung AI detect.

                        Request sẽ tập trung giám sát khu vực hồ nước/đập nước theo mục tiêu đã nêu. Anh/chị kiểm tra thông tin bên phải rồi tiếp tục.
                        """),
                Map.entry("FALLBACK_GENERIC_READY_NO_ADDON", """
                        Đã ghi nhận không cần dịch vụ bổ sung.

                        Request sẽ tập trung giám sát theo khu vực và mục tiêu anh/chị đã nhập. Anh/chị kiểm tra thông tin bên phải, nếu đúng có thể tiếp tục.
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

                        Anh/chị cần orthomosaic 2D, mô hình 3D, point cloud, đo diện tích/thể tích hay bản đồ hiện trạng? Chỉ cần trả lời phần nào thật sự cần.
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
