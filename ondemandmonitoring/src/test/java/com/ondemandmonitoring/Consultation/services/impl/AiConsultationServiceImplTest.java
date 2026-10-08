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
                        "svc-construction",
                        "Giám sát công trình"
                ),
                new AcceptanceCase(
                        "Tôi cần theo dõi công trường xây dựng.",
                        "svc-construction",
                        "Giám sát công trình"
                ),
                new AcceptanceCase(
                        "Tôi muốn kiểm tra mái nhà xưởng.",
                        "svc-factory",
                        "Kiểm tra nhà xưởng"
                ),
                new AcceptanceCase(
                        "Tôi cần chụp tổng quan khu vực.",
                        "svc-area",
                        "Giám sát khu vực"
                ),
                new AcceptanceCase(
                        "Tôi muốn giám sát rừng và khu vực cây xanh.",
                        "svc-forest",
                        "Giám sát rừng"
                ),
                new AcceptanceCase(
                        "Tôi muốn quan sát vị trí khó tiếp cận trong rừng.",
                        "svc-forest",
                        "Giám sát rừng"
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
                                    "Giám sát khu vực",
                                    "Chụp ảnh và video tổng quan một khu vực theo phạm vi yêu cầu.",
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
        }
    }

    @Test
    void closeRagScoresStillRecommendWhenCustomerEvidenceDirectlyMatchesTopService() throws Exception {
        Optional<AiConsultationResult> result = invokeRagRecommendation(
                "Tôi muốn được tư vấn dịch vụ phù hợp cho khu vực rừng.",
                "Địa chỉ/khu vực: Rừng phòng hộ.\nVùng map nhận diện: Rừng.",
                List.of(
                        new ServiceSearchCandidate(
                                "svc-forest",
                                "Giám sát rừng",
                                "Chụp ảnh và video khu vực rừng, ghi nhận hiện trạng và khu vực bất thường.",
                                0.87
                        ),
                        new ServiceSearchCandidate(
                                "svc-area",
                                "Giám sát khu vực",
                                "Chụp ảnh và video tổng quan một khu vực theo phạm vi giám sát.",
                                0.82
                        )
                )
        );

        assertThat(result).isPresent();
        assertThat(result.get().requirementStatus()).isEqualTo(ConsultationStatus.RECOMMENDED);
        assertThat(result.get().recommendedServiceId()).isEqualTo("svc-forest");
    }

    @Test
    void customerEvidenceCanRerankLowerSemanticCandidateBeforeCallingLlm() throws Exception {
        Optional<AiConsultationResult> result = invokeRagRecommendation(
                "kiểm tra là tiến độ",
                """
                        Địa chỉ/khu vực: Công trường xây dựng.
                        Vùng map nhận diện: Công trường xây dựng.
                        CUSTOMER: Tôi muốn giám sát công trình.
                        CUSTOMER: kiểm tra là tiến độ
                        """,
                List.of(
                        new ServiceSearchCandidate(
                                "svc-area",
                                "Giám sát khu vực",
                                "Chụp ảnh và video tổng quan một khu vực theo vị trí và phạm vi giám sát.",
                                0.788
                        ),
                        new ServiceSearchCandidate(
                                "svc-factory",
                                "Kiểm tra nhà xưởng",
                                "Quan sát mái, bề mặt và các khu vực khó tiếp cận của nhà xưởng.",
                                0.784
                        ),
                        new ServiceSearchCandidate(
                                "svc-construction",
                                "Giám sát công trình",
                                "Theo dõi công trình xây dựng, công trường, tiến độ thi công và đối chiếu hiện trạng bằng ảnh/video.",
                                0.782
                        )
                )
        );

        assertThat(result).isPresent();
        assertThat(result.get().requirementStatus()).isEqualTo(ConsultationStatus.RECOMMENDED);
        assertThat(result.get().recommendedServiceId()).isEqualTo("svc-construction");
    }

    @Test
    void ragRecommendationReturnsEmptyWhenScoresAreTooClose() throws Exception {
        Optional<AiConsultationResult> result = invokeRagRecommendation(
                "Tôi muốn dùng drone để kiểm tra.",
                "Tôi muốn dùng drone để kiểm tra.",
                List.of(
                        new ServiceSearchCandidate(
                                "svc-area",
                                "Giám sát khu vực",
                                "Giám sát một khu vực theo phạm vi yêu cầu.",
                                0.74
                        ),
                        new ServiceSearchCandidate(
                                "svc-factory",
                                "Kiểm tra nhà xưởng",
                                "Kiểm tra nhà xưởng bằng drone.",
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
        return invokeRagRecommendation(query, query, candidates);
    }

    private Optional<AiConsultationResult> invokeRagRecommendation(
            String query,
            String customerEvidence,
            List<ServiceSearchCandidate> candidates
    ) throws Exception {
        AiConsultationServiceImpl service = new AiConsultationServiceImpl(null, null, null);
        ReflectionTestUtils.setField(service, "recommendationMinScore", 0.72);
        ReflectionTestUtils.setField(service, "recommendationMinScoreGap", 0.08);

        Method method = AiConsultationServiceImpl.class.getDeclaredMethod(
                "buildRagRecommendation",
                String.class,
                String.class,
                List.class,
                String.class
        );
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Optional<AiConsultationResult> result = (Optional<AiConsultationResult>) method.invoke(
                service,
                query,
                customerEvidence,
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
