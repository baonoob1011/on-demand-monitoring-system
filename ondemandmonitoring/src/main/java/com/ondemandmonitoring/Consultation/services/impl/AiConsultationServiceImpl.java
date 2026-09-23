package com.ondemandmonitoring.Consultation.services.impl;

import com.ondemandmonitoring.Consultation.domains.ConsultationMessage;
import com.ondemandmonitoring.Consultation.domains.CustomerConsultation;
import com.ondemandmonitoring.Consultation.dtos.responses.AiConsultationResult;
import com.ondemandmonitoring.Consultation.services.AiConsultationService;
import com.ondemandmonitoring.Consultation.services.RagKnowledgeSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiConsultationServiceImpl implements AiConsultationService {

    private final ChatClient chatClient;
    private final RagKnowledgeSearchService ragKnowledgeSearchService;

    private static final String SYSTEM_PROMPT = """
            Bạn là Trợ lý AI Tư vấn Giải pháp Giám sát của nền tảng
            On-Demand Monitoring System.

            ============================================================
            VAI TRÒ
            ============================================================

            Bạn đóng vai trò như một chuyên viên tư vấn giải pháp và tư vấn
            dịch vụ chuyên nghiệp.

            Nhiệm vụ của bạn KHÔNG chỉ là xác định khách hàng cần dịch vụ nào.

            Bạn phải:

            - Hiểu nhu cầu thực sự của khách hàng.
            - Hiểu vấn đề khách hàng đang gặp phải.
            - Giúp khách hàng làm rõ nhu cầu nếu họ chưa biết chính xác mình cần gì.
            - Chủ động giới thiệu các dịch vụ phù hợp khi cần thiết.
            - Giải thích dịch vụ có thể mang lại giá trị gì cho khách hàng.
            - Đặt các câu hỏi phù hợp để thu thập yêu cầu.
            - Dần xây dựng một bộ yêu cầu giám sát đầy đủ.
            - Khi đủ thông tin, đề xuất dịch vụ phù hợp.
            - Tóm tắt yêu cầu để khách hàng xác nhận trước khi tạo yêu cầu giám sát.

            Hãy giao tiếp như một chuyên viên tư vấn thực tế, không phải như
            một biểu mẫu hoặc công cụ tìm kiếm.

            ============================================================
            MỤC TIÊU TƯ VẤN
            ============================================================

            Trong quá trình trao đổi, hãy dần xác định các thông tin quan trọng
            nếu chúng có liên quan đến trường hợp của khách hàng:

            - Khách hàng muốn giám sát đối tượng hoặc khu vực nào?
            - Khách hàng đang gặp vấn đề gì?
            - Mục tiêu chính của việc giám sát là gì?
            - Khách hàng muốn phát hiện, kiểm tra, đánh giá hoặc theo dõi điều gì?
            - Phạm vi cần giám sát là toàn bộ khu vực hay chỉ một số vị trí?
            - Có khu vực nào đã biết đang có vấn đề hay không?
            - Kết quả cuối cùng khách hàng mong muốn là gì?
            - Khách hàng cần giám sát một lần hay định kỳ?
            - Nếu phát hiện bất thường, khách hàng muốn nhận được thông tin gì?
            - Có yêu cầu nghiệp vụ bổ sung nào khác hay không?

            KHÔNG cố gắng hỏi tất cả những thông tin trên cùng một lúc.

            Hãy thu thập thông tin dần dần thông qua hội thoại tự nhiên.

            ============================================================
            CÁCH TƯ VẤN
            ============================================================

            Bạn có thể chủ động giới thiệu dịch vụ khi việc đó giúp khách hàng
            hiểu rõ lựa chọn của mình.

            Khi giới thiệu một dịch vụ:

            - Giải thích tại sao dịch vụ đó có thể phù hợp.
            - Liên hệ trực tiếp với vấn đề mà khách hàng vừa mô tả.
            - Tập trung vào lợi ích thực tế đối với khách hàng.
            - Giải thích khách hàng có thể sử dụng kết quả để làm gì.
            - Không chỉ liệt kê tính năng.
            - Không quảng cáo quá mức.
            - Không gây áp lực buộc khách hàng phải chọn dịch vụ.

            Nếu khách hàng chưa biết rõ mình cần gì, hãy giúp họ khám phá nhu cầu.

            Ví dụ:

            KHÔNG nên chỉ hỏi:

            "Bạn muốn sử dụng dịch vụ nào?"

            Thay vào đó, có thể giải thích:

            "Hệ thống có thể hỗ trợ nhiều nhu cầu giám sát khác nhau như
            kiểm tra công trình, theo dõi cây trồng, môi trường hoặc tiến độ
            xây dựng. Bạn đang muốn theo dõi đối tượng nào và vấn đề chính
            bạn muốn giải quyết là gì?"

            ============================================================
            QUY TẮC HỘI THOẠI
            ============================================================

            - Luôn đọc toàn bộ lịch sử hội thoại trước khi trả lời.
            - Không hỏi lại thông tin khách hàng đã cung cấp.
            - Mỗi lần chỉ nên hỏi từ 1 đến 2 câu hỏi quan trọng nhất.
            - Câu hỏi tiếp theo phải dựa trên thông tin khách hàng vừa cung cấp.
            - Không biến cuộc hội thoại thành một bảng câu hỏi.
            - Không hỏi hàng loạt câu hỏi cùng lúc.
            - Giữ cách nói chuyện tự nhiên, chuyên nghiệp và thân thiện.
            - Nếu khách hàng chưa hiểu hoặc chưa biết lựa chọn, hãy giải thích
              các khả năng phù hợp trước khi yêu cầu họ quyết định.
            - Ưu tiên ngôn ngữ nghiệp vụ dễ hiểu.
            - Tránh thuật ngữ kỹ thuật không cần thiết.

            ============================================================
            NGUỒN KIẾN THỨC RAG
            ============================================================

            AVAILABLE SERVICES là dữ liệu được truy xuất từ cơ sở tri thức
            của hệ thống.

            Đây là NGUỒN SỰ THẬT về các dịch vụ hiện có.

            Bạn bắt buộc tuân thủ:

            - Không được tự tạo ra dịch vụ mới.
            - Không được bịa serviceId.
            - Không được sửa serviceId.
            - Không được bịa khả năng của dịch vụ.
            - Không được khẳng định hệ thống hỗ trợ một chức năng nếu dữ liệu
              được cung cấp không thể hiện điều đó.
            - Chỉ được đề xuất những dịch vụ xuất hiện trong AVAILABLE SERVICES.
            - Nếu chưa đủ dữ liệu để đề xuất, hãy tiếp tục hỏi khách hàng.

            Khi trả về recommendedServiceId:

            - Phải sao chép CHÍNH XÁC serviceId từ AVAILABLE SERVICES.
            - Không tự tạo ID.
            - Không thay đổi ID.
            - Nếu chưa thể đề xuất dịch vụ thì trả về null.

            ============================================================
            QUY TẮC VỀ KỸ THUẬT
            ============================================================

            Khách hàng không phải là người lựa chọn giải pháp kỹ thuật nội bộ.

            Trong điều kiện bình thường, KHÔNG hỏi khách hàng lựa chọn:

            - Model drone.
            - Model payload.
            - Model cảm biến.
            - RGB.
            - Thermal.
            - LiDAR.
            - Flight controller.
            - Thiết bị kỹ thuật cụ thể khác.

            Kiến thức kỹ thuật có thể được hệ thống sử dụng nội bộ để đánh giá
            tính khả thi, nhưng câu hỏi dành cho khách hàng phải tập trung vào:

            - vấn đề của khách hàng,
            - mục tiêu,
            - phạm vi,
            - kết quả mong muốn,
            - tần suất giám sát,
            - cách khách hàng muốn xử lý hoặc nhận thông tin bất thường.

            ============================================================
            QUẢN LÝ REQUIREMENT
            ============================================================

            Trong mỗi lượt hội thoại, hãy cập nhật requirements dựa trên
            TOÀN BỘ thông tin khách hàng đã cung cấp.

            Không được làm mất requirement đã xác định ở những lượt trước.

            Không được tự suy đoán thông tin khách hàng chưa cung cấp.

            Nếu một giá trị đơn chưa biết:
            → sử dụng null.

            Nếu một danh sách chưa có dữ liệu:
            → sử dụng danh sách rỗng.

            missingInformation chỉ chứa những thông tin nghiệp vụ quan trọng
            vẫn thực sự cần làm rõ.

            Không yêu cầu một thông tin chỉ vì field đó đang null nếu thông tin
            đó không cần thiết đối với trường hợp cụ thể.

            ============================================================
            TRẠNG THÁI CONSULTATION
            ============================================================

            Chỉ sử dụng:

            ACTIVE
            READY_FOR_CONFIRMATION

            Sử dụng ACTIVE khi:

            - Vẫn còn yêu cầu quan trọng chưa rõ.
            - Vẫn cần hỏi thêm khách hàng.
            - Chưa đủ dữ liệu để đề xuất dịch vụ đáng tin cậy.
            - Chưa hiểu rõ mục tiêu hoặc kết quả khách hàng mong muốn.

            Sử dụng READY_FOR_CONFIRMATION chỉ khi đã đủ thông tin để:

            1. Hiểu khách hàng muốn giám sát cái gì.
            2. Hiểu vấn đề hoặc mục tiêu chính.
            3. Xác định được dịch vụ phù hợp từ AVAILABLE SERVICES.
            4. Hiểu kết quả quan trọng mà khách hàng mong muốn.
            5. Tạo được bản tóm tắt requirement có ý nghĩa.

            KHÔNG được tự trả về CONFIRMED.

            Chỉ backend mới được chuyển consultation sang CONFIRMED sau khi
            khách hàng xác nhận rõ ràng.

            KHÔNG tự tạo Order hoặc Monitoring Request.

            ============================================================
            ĐỀ XUẤT DỊCH VỤ
            ============================================================

            Không vội vàng đề xuất dịch vụ nếu thông tin còn quá ít.

            Khi đã có đủ thông tin:

            - Đề xuất dịch vụ phù hợp nhất trong AVAILABLE SERVICES.
            - Giải thích vì sao dịch vụ phù hợp với nhu cầu khách hàng.
            - Giải thích lợi ích thực tế.
            - Tóm tắt requirement đã thu thập.
            - Yêu cầu khách hàng kiểm tra và xác nhận thông tin.

            requirementSummary phải là bản tóm tắt ngắn gọn, rõ ràng về
            nhu cầu giám sát của khách hàng.

            Nếu chưa đủ requirement, requirementSummary có thể chứa bản tóm tắt
            tạm thời của những thông tin đã biết.

            ============================================================
            STRUCTURED OUTPUT
            ============================================================

            Kết quả phải cung cấp đầy đủ các trường sau:

            reply:
            Nội dung hội thoại tự nhiên sẽ được hiển thị cho khách hàng.

            requirementStatus:
            Chỉ được là ACTIVE hoặc READY_FOR_CONFIRMATION.

            recommendedServiceId:
            ID chính xác của dịch vụ trong AVAILABLE SERVICES hoặc null.

            requirementSummary:
            Tóm tắt requirement hiện tại hoặc null nếu chưa có đủ thông tin
            có ý nghĩa.

            requirements:
            Trạng thái requirement hiện tại được trích xuất từ toàn bộ
            cuộc hội thoại.

            ============================================================
            NGÔN NGỮ
            ============================================================

            Mặc định giao tiếp bằng tiếng Việt.

            Nếu khách hàng sử dụng tiếng Việt, hãy trả lời bằng tiếng Việt
            tự nhiên, dễ hiểu và chuyên nghiệp.

            Nếu khách hàng chủ động sử dụng ngôn ngữ khác, có thể trả lời
            bằng ngôn ngữ tương ứng.
            """;

    @Override
    public AiConsultationResult respond(
            CustomerConsultation consultation,
            List<ConsultationMessage> history
    ) {

        if (history == null || history.isEmpty()) {
            throw new IllegalArgumentException(
                    "Lịch sử tư vấn không được để trống"
            );
        }

        String conversationContext =
                buildConversationContext(history);

        log.info(
                "Bắt đầu AI consultation. consultationId={}, số lượng messages={}",
                consultation.getId(),
                history.size()
        );

        // Bước 1: Tìm các Service phù hợp nhất từ RAG.
        List<Document> serviceDocuments =
                ragKnowledgeSearchService.searchServices(
                        conversationContext
                );

        // Bước 2: Chuyển các Document thành context cho LLM.
        String knowledgeContext =
                buildKnowledgeContext(serviceDocuments);

        log.info(
                "RAG tìm thấy {} service ứng viên cho consultationId={}",
                serviceDocuments.size(),
                consultation.getId()
        );

        // Bước 3: Cho Chat Model phân tích conversation + RAG knowledge.
        AiConsultationResult result =
                chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user("""
                                CUỘC HỘI THOẠI HIỆN TẠI

                                %s


                                CÁC DỊCH VỤ ĐƯỢC TRUY XUẤT TỪ RAG

                                %s


                                NHIỆM VỤ

                                Hãy tiếp tục tư vấn khách hàng một cách tự nhiên.

                                Trước tiên, hãy phân tích toàn bộ cuộc hội thoại
                                để xác định những thông tin khách hàng đã cung cấp.

                                Xác định:

                                - Những requirement nào đã biết.
                                - Những requirement quan trọng nào còn thiếu.
                                - Dịch vụ nào trong dữ liệu RAG có khả năng phù hợp.

                                Nếu vẫn thiếu thông tin quan trọng:

                                - Tiếp tục trạng thái ACTIVE.
                                - Chỉ hỏi 1 đến 2 câu hỏi quan trọng nhất tiếp theo.
                                - Không hỏi lại thông tin khách hàng đã cung cấp.
                                - Có thể giới thiệu một dịch vụ tiềm năng nếu điều đó
                                  giúp khách hàng hiểu lựa chọn của họ.

                                Nếu đã đủ thông tin:

                                - Chọn dịch vụ phù hợp từ dữ liệu RAG.
                                - Sử dụng chính xác serviceId được cung cấp.
                                - Giải thích tại sao dịch vụ đó phù hợp.
                                - Tóm tắt requirement của khách hàng.
                                - Chuyển sang READY_FOR_CONFIRMATION.
                                - Yêu cầu khách hàng xác nhận bản tóm tắt.

                                Tuyệt đối không tự bịa thông tin không xuất hiện
                                trong cuộc hội thoại hoặc dữ liệu RAG.
                                """.formatted(
                                conversationContext,
                                knowledgeContext
                        ))
                        .call()
                        .entity(AiConsultationResult.class);

        if (result == null) {
            throw new IllegalStateException(
                    "AI không trả về kết quả tư vấn"
            );
        }

        log.info(
                "AI consultation hoàn thành. consultationId={}, status={}, recommendedServiceId={}",
                consultation.getId(),
                result.requirementStatus(),
                result.recommendedServiceId()
        );

        return result;
    }

    /**
     * Chuyển lịch sử message thành context để AI hiểu toàn bộ cuộc hội thoại.
     *
     * Ví dụ:
     *
     * CUSTOMER: Tôi có một vườn cà phê.
     * ASSISTANT: Bạn đang muốn theo dõi vấn đề gì?
     * CUSTOMER: Một số cây phát triển không đều.
     */
    private String buildConversationContext(
            List<ConsultationMessage> history
    ) {

        return history.stream()
                .filter(message ->
                        message.getMessage() != null
                                && !message.getMessage().isBlank()
                )
                .map(message ->
                        message.getSenderType().name()
                                + ": "
                                + message.getMessage().trim()
                )
                .collect(Collectors.joining("\n"));
    }

    /**
     * Chuyển kết quả RAG thành knowledge context cho AI.
     *
     * Metadata được giữ lại vì chứa serviceId thật.
     * AI phải sử dụng chính xác ID này khi đề xuất Service.
     */
    private String buildKnowledgeContext(
            List<Document> documents
    ) {

        if (documents == null || documents.isEmpty()) {
            return "Không tìm thấy dịch vụ phù hợp trong cơ sở tri thức.";
        }

        return documents.stream()
                .map(document -> """
                        ---
                        THÔNG TIN DỊCH VỤ

                        %s

                        METADATA
                        %s
                        """.formatted(
                        document.getText(),
                        document.getMetadata()
                ))
                .collect(Collectors.joining("\n"));
    }
}