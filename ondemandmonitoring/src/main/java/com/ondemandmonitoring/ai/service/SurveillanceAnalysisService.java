package com.ondemandmonitoring.ai.service;

import com.ondemandmonitoring.ai.dto.SurveillanceDtos.*;
import com.ondemandmonitoring.ai.infrastructure.OpenRouterAiClient;
import com.ondemandmonitoring.categoryservice.domain.CategoryService;
import com.ondemandmonitoring.categoryservice.repository.CategoryServiceRepository;
import com.ondemandmonitoring.warehouse.domain.PreferredTime;
import com.ondemandmonitoring.warehouse.repository.PreferredTimeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class SurveillanceAnalysisService {

  private final OpenRouterAiClient aiClient;
  private final CategoryServiceRepository categoryServiceRepository;
  private final PreferredTimeRepository preferredTimeRepository;
  private final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule());

  public SurveillanceAnalysisService(
      OpenRouterAiClient aiClient,
      CategoryServiceRepository categoryServiceRepository,
      PreferredTimeRepository preferredTimeRepository) {
    this.aiClient = aiClient;
    this.categoryServiceRepository = categoryServiceRepository;
    this.preferredTimeRepository = preferredTimeRepository;
  }

  public AnalysisResult analyzeRequest(SurveillanceAnalysisRequest req) {
    CategoryService serviceEntity = null;
    if (req.getServiceId() != null && !req.getServiceId().isBlank()) {
      serviceEntity = categoryServiceRepository.findById(req.getServiceId()).orElse(null);
    }

    PreferredTime preferredTimeEntity = null;
    if (req.getPreferredTimeId() != null && !req.getPreferredTimeId().isBlank()) {
      preferredTimeEntity = preferredTimeRepository.findById(req.getPreferredTimeId()).orElse(null);
    }

    String systemPrompt = buildSystemPrompt();
    String userContent = buildUserContent(req, serviceEntity, preferredTimeEntity);

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
        Nhiệm vụ của bạn là phân tích tính khả thi và hợp lý của yêu cầu giám sát đơn hàng để giảm thiểu việc bị Điều hành viên (Operator) từ chối.

        Quy tắc phân tích:
        1. Service vs Purpose/Description: Mục tiêu và mô tả có phù hợp với dịch vụ giám sát (Category Service name & description) chọn trong CSDL hay không?
        2. Media Type & Parameters vs Purpose: Định dạng ảnh (IMAGE) / video (VIDEO), số lượng ảnh (numberOfPhoto) hoặc thời lượng video (durationOfVideo) có đáp ứng được mục tiêu hay không? (Ví dụ: Đo đạc/dựng bản đồ cần IMAGE phân giải cao, đếm/quan sát chuyển động cần VIDEO).
        3. Monitoring Time & Lighting: Khung giờ giám sát (Preferred Time start - end) và ngày (Preferred Date) có đủ điều kiện ánh sáng quang học hay không? Khung giờ đêm (18:30 - 05:30) không thể giám sát quang học thông thường nếu không có thiết bị cảm biến nhiệt/hồng ngoại đặc thù.
        4. Lead Time: Thời điểm thực hiện (Preferred Date + Preferred Time Start) so với thời gian hiện tại của hệ thống có quá gấp (dưới 2 giờ) hoặc đã ở quá khứ hay không?
        5. Location & Detail: Địa chỉ (address), tọa độ (point), và chi tiết mô tả có đủ thông tin để vận hành thực thi hay không?

        Quy định phân loại Status:
        - 'PASSED': Hợp lý, khả thi, thông tin rõ ràng.
        - 'WARNING': Có điểm chưa tối ưu (ví dụ: số lượng ảnh/thời lượng video chưa phù hợp, mô tả sơ sài) nhưng vẫn có thể cân nhắc thực hiện.
        - 'REJECTED': Bất khả thi về mặt vật lý (giám sát ban đêm bằng camera thông thường, thời gian đã trôi qua, loại dịch vụ mâu thuẫn hoàn toàn, thông tin dịch vụ/khung giờ không tồn tại).

        BẮT BUỘC chỉ trả về 1 JSON Object duy nhất, không thêm Markdown fence, theo đúng cấu trúc:
        {
          "status": "PASSED" | "WARNING" | "REJECTED",
          "is_feasible": true | false,
          "summary": "Tóm tắt đánh giá ngắn gọn trong 1-2 câu",
          "issues": [
            {
              "field": "serviceId" | "purpose" | "description" | "preferredDate" | "preferredTimeId" | "mediaType" | "address" | "point" | "durationOfVideo" | "numberOfPhoto",
              "issue_type": "MISMATCH" | "POOR_LIGHTING" | "SUBOPTIMAL_MEDIA" | "INSUFFICIENT_DETAIL" | "UNREALISTIC_TIME" | "INVALID_RESOURCE",
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

  private String buildUserContent(
      SurveillanceAnalysisRequest req,
      CategoryService service,
      PreferredTime preferredTime) {
    String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    String serviceInfo = (service != null)
        ? String.format("%s (Mô tả dịch vụ: %s)", service.getName(), service.getDescription() != null ? service.getDescription() : "Không có")
        : "Không tìm thấy dịch vụ trong CSDL cho ID: " + req.getServiceId();

    String timeInfo = (preferredTime != null)
        ? String.format("%s (%s - %s)", preferredTime.getName(), preferredTime.getStartTime(), preferredTime.getEndTime())
        : "Không tìm thấy khung giờ trong CSDL cho ID: " + req.getPreferredTimeId();

    String pointInfo = (req.getPoint() != null && req.getPoint().getLongitude() != null && req.getPoint().getLatitude() != null)
        ? String.format("Tọa độ [Kinh độ: %s, Vĩ độ: %s]", req.getPoint().getLongitude(), req.getPoint().getLatitude())
        : "Chưa cung cấp tọa độ";

    String mediaDetails = "";
    if (req.getMediaType() != null) {
      if (req.getMediaType().name().equalsIgnoreCase("IMAGE")) {
        mediaDetails = String.format("IMAGE (Số lượng ảnh: %s)", req.getNumberOfPhoto() != null ? req.getNumberOfPhoto() : "Chưa nhập");
      } else if (req.getMediaType().name().equalsIgnoreCase("VIDEO")) {
        mediaDetails = String.format("VIDEO (Thời lượng: %s giây)", req.getDurationOfVideo() != null ? req.getDurationOfVideo() : "Chưa nhập");
      }
    }

    return String.format("""
        Thời gian hiện tại của hệ thống: %s
        Dữ liệu người dùng nhập trên form:
        - Tiêu đề (Title): %s
        - Dịch vụ giám sát (Category Service): %s (ID: %s)
        - Mục tiêu giám sát (Purpose): %s
        - Miêu tả chi tiết (Description): %s
        - Địa chỉ giám sát (Address): %s
        - Tọa độ địa lý (Point): %s
        - Ngày giám sát mong muốn (Preferred Date): %s
        - Khung giờ mong muốn (Preferred Time): %s (ID: %s)
        - Yêu cầu kết quả (Media Type): %s
        """,
        currentTime,
        req.getTitle() != null ? req.getTitle() : "Chưa nhập",
        serviceInfo,
        req.getServiceId(),
        req.getPurpose() != null ? req.getPurpose() : "Chưa nhập",
        req.getDescription() != null ? req.getDescription() : "Chưa nhập",
        req.getAddress() != null ? req.getAddress() : "Chưa nhập",
        pointInfo,
        req.getPreferredDate() != null ? req.getPreferredDate().toString() : "Chưa chọn",
        timeInfo,
        req.getPreferredTimeId(),
        mediaDetails);
  }
}