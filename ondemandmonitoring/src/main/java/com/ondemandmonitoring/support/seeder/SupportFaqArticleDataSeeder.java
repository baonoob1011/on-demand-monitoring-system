package com.ondemandmonitoring.support.seeder;

import com.ondemandmonitoring.support.domain.SupportFaqArticle;
import com.ondemandmonitoring.support.repository.SupportFaqArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupportFaqArticleDataSeeder implements CommandLineRunner {

    private final SupportFaqArticleRepository articleRepository;

    @Override
    public void run(String... args) {
        if (articleRepository.count() > 0) {
            log.info("Support FAQ Articles table already populated ({} articles found)", articleRepository.count());
            return;
        }

        log.info("Seeding initial FAQ Articles into support_faq_articles table...");

        List<SupportFaqArticle> initialArticles = List.of(
                create("ord-1", "ORDERS", "Đơn hàng",
                        "Tại sao yêu cầu giám sát của tôi vẫn đang chờ phê duyệt?",
                        "Yêu cầu giám sát sẽ ở trạng thái CHỜ PHÊ DUYỆT trong khi quản lý vận hành xác minh vùng không phận, dự báo thời tiết và phân công thiết bị drone phù hợp cùng phi công được chứng nhận. Quá trình phê duyệt thường hoàn tất trong vòng 15–30 phút trong giờ làm việc.",
                        "chờ,phê duyệt,đang xử lý,tại sao,trễ,yêu cầu,trạng thái,pending"),

                create("ord-2", "ORDERS", "Đơn hàng",
                        "Tại sao yêu cầu giám sát của tôi bị từ chối?",
                        "Yêu cầu có thể bị từ chối do vùng không phận bị hạn chế tạm thời (NOTAM), cảnh báo thời tiết xấu hoặc tọa độ GPS không hợp lệ. Bạn sẽ nhận được thông báo chính thức với lý do từ chối cụ thể cùng hướng dẫn điều chỉnh và gửi lại.",
                        "từ chối,bị hủy,không phận,thời tiết,tại sao,rejected"),

                create("ord-3", "ORDERS", "Đơn hàng",
                        "Tôi có thể thay đổi lịch giám sát sau khi đã gửi không?",
                        "Có, bạn có thể yêu cầu thay đổi lịch bay tối đa 2 giờ trước thời điểm bay đã định. Hãy vào trang Chi tiết đơn hàng và chọn \"Thay đổi lịch bay\". Nếu nhiệm vụ đã ở trạng thái TIỀN KIỂM hoặc ĐANG BAY, vui lòng liên hệ Bộ phận hỗ trợ trực tiếp.",
                        "thay đổi,sửa,lịch bay,đổi lịch,thời gian,ngày"),

                create("ord-4", "ORDERS", "Đơn hàng",
                        "Tôi có thể hủy yêu cầu giám sát không?",
                        "Bạn có thể hủy yêu cầu ở trạng thái CHỜ PHÊ DUYỆT hoặc ĐÃ DUYỆT mà không bị phạt. Khi nhiệm vụ đã ở ĐANG BAY, việc hủy phải tuân theo quy trình an toàn bay.",
                        "hủy,dừng,hủy bỏ,cancel,xóa"),

                create("msn-1", "MISSIONS", "Nhiệm vụ bay",
                        "Tại sao nhiệm vụ drone của tôi chưa bắt đầu?",
                        "Nhiệm vụ sẽ bắt đầu đúng khung giờ đã lên lịch sau khi qua kiểm tra tự động tiền chuyến bay 100%. Nếu drone đang thực hiện hiệu chỉnh pin hoặc chờ khóa telemetry, việc khởi hành có thể bị trễ 2–5 phút.",
                        "chưa bắt đầu,trễ,khởi động,bay,nhiệm vụ,chờ,mission"),

                create("msn-2", "MISSIONS", "Nhiệm vụ bay",
                        "Tại sao drone được phân công của tôi bị thay thế?",
                        "Hệ thống quản lý đội bay tự động thay thế drone nếu telemetry tiền chuyến phát hiện mất cân bằng pin, sai lệch nhiệt động cơ, hoặc cảnh báo hiệu chỉnh cảm biến. Drone dự phòng được phân công ngay lập tức để đảm bảo nhiệm vụ không thất bại.",
                        "thay drone,đổi drone,drone khác,thay thế,hardware,tiền kiểm"),

                create("msn-3", "MISSIONS", "Nhiệm vụ bay",
                        "Điều gì xảy ra nếu nhiệm vụ không qua kiểm tra tiền chuyến?",
                        "Khi kiểm tra tiền chuyến thất bại (FAILED_PREFLIGHT), drone bị ảnh hưởng sẽ tự động được đưa vào bảo trì. Người vận hành hệ thống sẽ phân công drone khả dụng khác trong vòng 10 phút hoặc lên lịch lại chuyến bay mà không tính thêm chi phí.",
                        "tiền kiểm thất bại,lỗi kiểm tra,preflight,bảo trì,thất bại"),

                create("msn-4", "MISSIONS", "Nhiệm vụ bay",
                        "Tại sao người vận hành dừng hoặc đổi lịch chuyến bay của tôi?",
                        "Người vận hành hệ thống có thể tạm dừng hoặc dừng chuyến bay khi gió giật vượt ngưỡng an toàn (>12 m/s), tầm nhìn hạn chế, hoặc hạn chế không phận khẩn cấp. An toàn luôn là ưu tiên hàng đầu.",
                        "dừng,tạm dừng,đổi lịch,gió,vận hành viên,thời tiết"),

                create("res-1", "RESULTS", "Kết quả giám sát",
                        "Tôi xem và tải kết quả giám sát ở đâu?",
                        "Khi xử lý telemetry sau bay hoàn tất, hãy vào Chi tiết đơn hàng → Tab Kết quả giám sát. Bạn có thể xem ảnh orthomosaic 4K, bản đồ nhiệt và tải xuống gói GeoTIFF / ZIP độ phân giải cao trực tiếp.",
                        "xem,tải xuống,kết quả,ảnh,hình ảnh,báo cáo,download"),

                create("res-2", "RESULTS", "Kết quả giám sát",
                        "Tại sao kết quả của tôi vẫn đang xử lý hoặc thiếu video?",
                        "Media sau bay trải qua quá trình ghép ảnh AI tự động và kiểm soát chất lượng. Render video 4K và lập bản đồ nhiệt thường hoàn tất trong vòng 15–20 phút sau khi hạ cánh.",
                        "đang xử lý,thiếu,video,ghép ảnh,chờ,tải"),

                create("res-3", "RESULTS", "Kết quả giám sát",
                        "Dữ liệu giám sát được lưu trữ trên đám mây bao lâu?",
                        "Mặc định, video thô được lưu 90 ngày và ảnh orthomosaic đã xử lý được lưu 365 ngày. Bạn có thể mở rộng thời gian lưu trữ vô thời hạn trong Cài đặt tài khoản.",
                        "lưu trữ,bao lâu,hết hạn,đám mây,thời gian"),

                create("res-4", "RESULTS", "Kết quả giám sát",
                        "Tại sao kết quả giám sát của tôi không đầy đủ?",
                        "Nếu drone quay về sớm do pin yếu hoặc thời tiết xấu, kết quả sẽ chỉ bao gồm một phần khu vực giám sát. Hệ thống sẽ tự động lên lịch nhiệm vụ bổ sung cho khu vực còn lại.",
                        "không đầy đủ,thiếu,vùng phủ,bị cắt ngắn,một phần"),

                create("med-1", "MEDIA", "Media & Phát trực tiếp",
                        "Tại sao livestream không khả dụng trong khi bay?",
                        "Phát trực tiếp yêu cầu kết nối telemetry 5G/LTE đang hoạt động. Tại các hành lang bay xa có tín hiệu di động yếu, livestream sẽ chuyển sang chế độ ghi đệm. Video HD đầy đủ sẽ tự động tải lên sau khi hạ cánh.",
                        "livestream,trực tiếp,không có,màn hình đen,5g,telemetry"),

                create("med-2", "MEDIA", "Media & Phát trực tiếp",
                        "Tại sao video upload bị thất bại?",
                        "Video tự động thử lại tải lên tối đa 3 lần qua Wi-Fi trạm mặt đất. Nếu kết nối mạng bị gián đoạn, hãy bấm \"Thử lại tải lên\" trên màn hình Quản lý Media.",
                        "upload lỗi,tải lên thất bại,video lỗi,thử lại"),

                create("med-3", "MEDIA", "Media & Phát trực tiếp",
                        "Làm thế nào để chia sẻ livestream với nhóm hiện trường?",
                        "Trong trình phát livestream, bấm \"Chia sẻ liên kết phát\". Bạn có thể tạo URL tạm thời an toàn kèm tùy chọn mã bảo vệ cho các bên liên quan tại hiện trường.",
                        "chia sẻ,link phát,truy cập,nhóm,mời"),

                create("sch-1", "SCHEDULING", "Lên lịch bay",
                        "Khung giờ bay hoạt động là khi nào?",
                        "Hoạt động bay tiêu chuẩn diễn ra hàng ngày từ 06:00 đến 18:00 (trong giờ ban ngày). Giám sát ban đêm yêu cầu chứng nhận nhiệt đặc biệt và đặt lịch trước với điều phối viên.",
                        "giờ bay,khung giờ,ban đêm,lịch bay,khi nào"),

                create("acc-1", "ACCOUNT", "Tài khoản & Quyền truy cập",
                        "Làm thế nào để thêm thành viên vào tổ chức của tôi?",
                        "Vào Cài đặt tổ chức → Thành viên nhóm và bấm \"Mời thành viên\". Phân quyền vai trò như Người xem, Quản lý hoặc Quản trị viên thanh toán.",
                        "mời,nhóm,thành viên,người dùng,vai trò,quyền")
        );

        articleRepository.saveAll(initialArticles);
        log.info("Successfully seeded {} FAQ Articles into database!", initialArticles.size());
    }

    private SupportFaqArticle create(String articleId, String category, String categoryLabel, String question, String answer, String keywords) {
        SupportFaqArticle article = new SupportFaqArticle();
        article.setArticleId(articleId);
        article.setCategory(category);
        article.setCategoryLabel(categoryLabel);
        article.setQuestion(question);
        article.setAnswer(answer);
        article.setKeywords(keywords);
        article.setIsPublished(true);
        return article;
    }
}
