package com.ondemandmonitoring.ai.service;

import com.ondemandmonitoring.ai.dto.SurveillanceDtos.*;
import com.ondemandmonitoring.ai.infrastructure.OpenRouterAiClient;
import com.ondemandmonitoring.categoryservice.domain.CategoryService;
import com.ondemandmonitoring.categoryservice.repository.CategoryServiceRepository;
import com.ondemandmonitoring.order.dto.GeoJsonPointDto;
import com.ondemandmonitoring.order.enums.MediaTypeSp;
import com.ondemandmonitoring.warehouse.domain.PreferredTime;
import com.ondemandmonitoring.warehouse.enums.PreferredTimeCode;
import com.ondemandmonitoring.warehouse.repository.PreferredTimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SurveillanceAnalysisServiceTest {

    @Mock
    private OpenRouterAiClient aiClient;

    @Mock
    private CategoryServiceRepository categoryServiceRepository;

    @Mock
    private PreferredTimeRepository preferredTimeRepository;

    private SurveillanceAnalysisService analysisService;

    @BeforeEach
    void setUp() {
        analysisService = new SurveillanceAnalysisService(aiClient, categoryServiceRepository, preferredTimeRepository);
    }

    @Test
    void analyzeRequest_Success_WithDatabaseEntities() {
        // Arrange
        String serviceId = "service-123";
        String preferredTimeId = "time-456";

        CategoryService categoryService = CategoryService.builder()
                .name("Giám sát nông nghiệp")
                .description("Theo dõi sức khỏe cây trồng và sâu bệnh")
                .build();

        PreferredTime preferredTime = PreferredTime.builder()
                .code(PreferredTimeCode.MORNING)
                .name("Sáng sớm")
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(10, 0))
                .build();

        when(categoryServiceRepository.findById(serviceId)).thenReturn(Optional.of(categoryService));
        when(preferredTimeRepository.findById(preferredTimeId)).thenReturn(Optional.of(preferredTime));

        String mockAiResponseJson = """
            {
              "status": "PASSED",
              "is_feasible": true,
              "summary": "Yêu cầu giám sát nông nghiệp hoàn toàn hợp lý.",
              "issues": [],
              "suggestions": ["Có thể bổ sung thêm ghi chú về loại cây trồng"]
            }
            """;
        when(aiClient.generateCompletion(anyString(), anyString())).thenReturn(mockAiResponseJson);

        GeoJsonPointDto point = GeoJsonPointDto.of(106.660172, 10.762622);
        SurveillanceAnalysisRequest request = SurveillanceAnalysisRequest.builder()
                .title("Giám sát ruộng lúa")
                .purpose("Đánh giá tình hình sâu bệnh")
                .serviceId(serviceId)
                .description("Giám sát khu vực 5ha lúa mì")
                .address("Củ Chi, TP.HCM")
                .point(point)
                .preferredDate(LocalDate.of(2026, 10, 15))
                .preferredTimeId(preferredTimeId)
                .mediaType(MediaTypeSp.IMAGE)
                .numberOfPhoto(20)
                .build();

        // Act
        AnalysisResult result = analysisService.analyzeRequest(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("PASSED");
        assertThat(result.isFeasible()).isTrue();
        assertThat(result.getSummary()).isEqualTo("Yêu cầu giám sát nông nghiệp hoàn toàn hợp lý.");

        ArgumentCaptor<String> userContentCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiClient).generateCompletion(anyString(), userContentCaptor.capture());

        String userContent = userContentCaptor.getValue();
        assertThat(userContent).contains("Giám sát nông nghiệp");
        assertThat(userContent).contains("Theo dõi sức khỏe cây trồng và sâu bệnh");
        assertThat(userContent).contains("Sáng sớm");
        assertThat(userContent).contains("07:00 - 10:00");
        assertThat(userContent).contains("IMAGE (Số lượng ảnh: 20)");
        assertThat(userContent).contains("Kinh độ: 106.660172, Vĩ độ: 10.762622");
    }

    @Test
    void analyzeRequest_Fallback_WhenAiResponseIsNull() {
        // Arrange
        String serviceId = "service-999";
        String preferredTimeId = "time-999";

        when(categoryServiceRepository.findById(serviceId)).thenReturn(Optional.empty());
        when(preferredTimeRepository.findById(preferredTimeId)).thenReturn(Optional.empty());
        when(aiClient.generateCompletion(anyString(), anyString())).thenReturn(null);

        SurveillanceAnalysisRequest request = SurveillanceAnalysisRequest.builder()
                .title("Kiểm tra công trình")
                .serviceId(serviceId)
                .preferredTimeId(preferredTimeId)
                .mediaType(MediaTypeSp.VIDEO)
                .durationOfVideo(60)
                .build();

        // Act
        AnalysisResult result = analysisService.analyzeRequest(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("WARNING");
        assertThat(result.isFeasible()).isTrue();
        assertThat(result.getSummary()).contains("Không nhận được phản hồi từ AI kiểm duyệt");
    }
}
