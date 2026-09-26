package com.ondemandmonitoring.Consultation.services.impl;

import com.ondemandmonitoring.Consultation.domains.ConsultationMessage;
import com.ondemandmonitoring.Consultation.domains.CustomerConsultation;
import com.ondemandmonitoring.Consultation.dtos.responses.AiConsultationResult;
import com.ondemandmonitoring.Consultation.dtos.responses.ServiceSearchCandidate;
import com.ondemandmonitoring.Consultation.enums.ConsultationSenderType;
import com.ondemandmonitoring.Consultation.enums.ConsultationStatus;
import com.ondemandmonitoring.Consultation.services.RagKnowledgeSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AiConsultationServiceImplTest {

    @Test
    void clearRagMatchesReturnRecommendedServiceWithoutClarification() throws Exception {
        List<AcceptanceCase> cases = List.of(
                new AcceptanceCase(
                        "Tôi muốn theo dõi tiến độ thi công của công trình.",
                        "svc-progress",
                        "Giám sát Tiến độ Xây dựng"
                ),
                new AcceptanceCase(
                        "Tôi cần theo dõi công trường xây dựng.",
                        "svc-progress",
                        "Giám sát Tiến độ Xây dựng"
                ),
                new AcceptanceCase(
                        "Tôi muốn đo điểm nóng.",
                        "svc-hotspot",
                        "Đo nhiệt độ / Điểm nhiệt"
                ),
                new AcceptanceCase(
                        "Tôi cần theo dõi mặt nước và dòng chảy.",
                        "svc-water",
                        "Giám sát Mặt nước / Dòng chảy"
                ),
                new AcceptanceCase(
                        "Tôi muốn giám sát đập nước và mực nước hồ chứa.",
                        "svc-dam",
                        "Giám sát Đập nước / Hồ chứa"
                ),
                new AcceptanceCase(
                        "Tôi cần đo nhiệt độ và áp suất khu vực bay.",
                        "svc-temp-pressure",
                        "Đo nhiệt độ / Áp suất"
                ),
                new AcceptanceCase(
                        "Tôi muốn kiểm tra công trình thủy lợi và cửa xả.",
                        "svc-hydraulic",
                        "Kiểm tra Công trình thủy lợi"
                )
        );

        for (AcceptanceCase acceptanceCase : cases) {
            Optional<AiConsultationResult> result = invokeRagRecommendation(
                    acceptanceCase.query(),
                    List.of(
                            new ServiceSearchCandidate(
                                    acceptanceCase.serviceId(),
                                    acceptanceCase.serviceName(),
                                    acceptanceCase.serviceName(),
                                    0.91
                            ),
                            new ServiceSearchCandidate(
                                    "svc-other",
                                    "Giám sát Đập nước / Hồ chứa",
                                    "Giám sát đập nước và hồ chứa.",
                                    0.62
                            )
                    )
            );

            assertThat(result).as(acceptanceCase.query()).isPresent();
            assertThat(result.get().requirementStatus()).isEqualTo(ConsultationStatus.RECOMMENDED);
            assertThat(result.get().recommendedServiceId()).isEqualTo(acceptanceCase.serviceId());
            assertThat(result.get().reply())
                    .contains(acceptanceCase.serviceName())
                    .contains(acceptanceCase.query())
                    .contains("Bạn có muốn bổ sung AI phân tích hình ảnh")
                    .contains("có thể phát sinh thêm chi phí");

            if (acceptanceCase.serviceName().equals("Giám sát Tiến độ Xây dựng")) {
                assertThat(result.get().requestTitle())
                        .isEqualTo("Giám sát tiến độ thi công công trình");
                assertThat(result.get().requestSummary())
                        .isEqualTo("Ghi nhận hình ảnh và video hiện trạng công trường để theo dõi và đối chiếu tiến độ thi công.");
            }
        }
    }

    @Test
    void ragRecommendationReturnsEmptyWhenScoresAreTooClose() throws Exception {
        Optional<AiConsultationResult> result = invokeRagRecommendation(
                "Tôi muốn dùng drone để kiểm tra.",
                List.of(
                        new ServiceSearchCandidate(
                                "svc-dam",
                                "Giám sát Đập nước / Hồ chứa",
                                "Giám sát đập nước và hồ chứa.",
                                0.74
                        ),
                        new ServiceSearchCandidate(
                                "svc-water",
                                "Giám sát Mặt nước / Dòng chảy",
                                "Giám sát mặt nước và dòng chảy.",
                                0.71
                        )
                )
        );

        assertThat(result).isEmpty();
    }

    @Test
    void noRagCandidateReturnsNeedMoreInfoWithoutCallingLlm() {
        RagKnowledgeSearchService searchService = new RagKnowledgeSearchService() {
            @Override
            public List<Document> search(String query) {
                return List.of();
            }

            @Override
            public List<ServiceSearchCandidate> searchServices(String query) {
                return List.of();
            }

            @Override
            public List<Document> searchServiceKnowledge(String query, String serviceId) {
                return List.of();
            }
        };

        AiConsultationServiceImpl service = new AiConsultationServiceImpl(null, searchService, null);
        CustomerConsultation consultation = new CustomerConsultation();
        consultation.setId("consultation-3");

        AiConsultationResult result = service.respond(
                consultation,
                List.of(ConsultationMessage.builder()
                        .senderType(ConsultationSenderType.CUSTOMER)
                        .message("Tôi muốn dùng drone để kiểm tra.")
                        .build())
        );

        assertThat(result.requirementStatus()).isEqualTo(ConsultationStatus.NEED_MORE_INFO);
        assertThat(result.recommendedServiceId()).isNull();
    }

    private Optional<AiConsultationResult> invokeRagRecommendation(
            String query,
            List<ServiceSearchCandidate> candidates
    ) throws Exception {
        AiConsultationServiceImpl service = new AiConsultationServiceImpl(null, null, null);
        ReflectionTestUtils.setField(service, "recommendationMinScore", 0.72);
        ReflectionTestUtils.setField(service, "recommendationMinScoreGap", 0.08);

        Method method = AiConsultationServiceImpl.class.getDeclaredMethod(
                "buildRagRecommendation",
                String.class,
                List.class,
                String.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Optional<AiConsultationResult> result = (Optional<AiConsultationResult>) method.invoke(
                service,
                query,
                candidates,
                "consultation-test"
        );

        return result;
    }

    private record AcceptanceCase(
            String query,
            String serviceId,
            String serviceName
    ) {
    }
}
