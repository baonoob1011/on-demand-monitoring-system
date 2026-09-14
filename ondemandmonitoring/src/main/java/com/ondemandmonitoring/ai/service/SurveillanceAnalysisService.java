package com.ondemandmonitoring.ai.service;

import com.ondemandmonitoring.ai.dto.SurveillanceDtos.*;
import com.ondemandmonitoring.ai.infrastructure.OpenRouterAiClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class SurveillanceAnalysisService {

  private final OpenRouterAiClient aiClient;
  private final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule());

  public SurveillanceAnalysisService(OpenRouterAiClient aiClient) {
    this.aiClient = aiClient;
  }

  public AnalysisResult analyzeRequest(SurveillanceAnalysisRequest req) {
    String systemPrompt = buildSystemPrompt();
    String userContent = buildUserContent(req);

    try {
      String jsonText = aiClient.generateCompletion(systemPrompt, userContent);

      if (jsonText != null && !jsonText.isBlank()) {
        return objectMapper.readValue(jsonText, AnalysisResult.class);
      }
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Lỗi giải mã JSON từ phản hồi AI: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new RuntimeException("Lỗi giao tiếp với OpenRouter: " + e.getMessage(), e);
    }

    AnalysisResult fallback = new AnalysisResult();
    fallback.setStatus("WARNING");
    fallback.setFeasible(true);
    fallback.setSummary("Không nhận được phản hồi từ AI kiểm duyệt, cho phép tiếp tục.");
    return fallback;
  }

  private String buildSystemPrompt() {
    return """
        Bạn là Chuyên gia Tiền Kiểm duyệt Yêu cầu Giám sát Không gian & Địa điểm (Drone/Camera/Cảm biến).
        Nhiệm vụ của bạn là phân tích tính khả thi và hợp lý của yêu cầu để giảm thiểu việc bị Điều hành viên (Operator) từ chối.

        Quy tắc phân tích:
        1. Service vs Purpose/Description: Mục tiêu và mô tả có đúng loại dịch vụ không? (Ví dụ: dịch vụ Nông nghiệp nhưng mô tả lại đếm xe tải xây dựng -> Bất hợp lý).
        2. Media Type vs Purpose: Định dạng ảnh/video có đáp ứng được mục tiêu không? (Ví dụ: Đo đạc/dựng bản đồ/đếm chi tiết cây trồng cần IMAGE phân giải cao, nếu chọn VIDEO là lãng phí hoặc kém hiệu quả. Quan sát máy móc chuyển động/lưu lượng xe thì cần VIDEO).
        3. Start DateTime vs Service/Equipment: Khung giờ giám sát có đủ ánh sáng quang học không? Khung giờ đêm (18:30 - 05:30) nếu không có yêu cầu camera hồng ngoại/nhiệt thì không thể giám sát quang học thông thường (như đếm lúa, xem tiến độ xây thô).
        4. Lead Time: Thời gian bắt đầu so với thời gian tạo đơn có quá gấp (dưới 2 giờ) khiến đội vận hành không kịp chuẩn bị không?
        5. Tính rõ ràng: Mô tả có đủ chi tiết để phi công/thiết bị thực hiện không?

        Quy định phân loại Status:
        - 'PASSED': Hợp lý, khả thi, thông tin rõ ràng.
        - 'WARNING': Có điểm chưa tối ưu (ví dụ: media type chưa chuẩn, mô tả hơi ngắn) nhưng vẫn có thể cân nhắc thực hiện.
        - 'REJECTED': Bất khả thi về mặt vật lý (giám sát ban đêm bằng camera thường, thời gian đã qua, nội dung mâu thuẫn hoàn toàn).

        BẮT BUỘC chỉ trả về 1 JSON Object duy nhất, không thêm Markdown fence, theo đúng cấu trúc:
        {
          "status": "PASSED" | "WARNING" | "REJECTED",
          "is_feasible": true | false,
          "summary": "Tóm tắt đánh giá ngắn gọn trong 1-2 câu",
          "issues": [
            {
              "field": "service" | "purpose" | "description" | "startDateTime" | "mediaType",
              "issue_type": "MISMATCH" | "POOR_LIGHTING" | "SUBOPTIMAL_MEDIA" | "INSUFFICIENT_DETAIL" | "UNREALISTIC_TIME",
              "message": "Giải thích chi tiết vì sao có vấn đề"
            }
          ],
          "suggestions": [
            "Gợi ý hành động người dùng nên sửa trên form"
          ],
          "refined_description": "Đoạn mô tả chi tiết gợi ý lại cho người dùng nếu mô tả ban đầu quá sơ sài (hoặc null nếu đã tốt)"
        }
        """;
  }

  private String buildUserContent(SurveillanceAnalysisRequest req) {
    String formattedTime = req.getStartDateTime() != null
        ? req.getStartDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        : "Chưa chọn";
    String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    return String.format("""
        Thời gian hiện tại của hệ thống: %s
        Dữ liệu người dùng nhập trên form:
        - Tiêu đề (Title): %s
        - Loại dịch vụ (Service): %s
        - Mục tiêu giám sát (Purpose): %s
        - Miêu tả chi tiết (Description): %s
        - Thời gian bắt đầu (Start DateTime): %s
        - Loại kết quả mong muốn (Media Type): %s
        """,
        currentTime,
        req.getTitle(),
        req.getService(),
        req.getPurpose(),
        req.getDescription(),
        formattedTime,
        req.getMediaType());
  }
}