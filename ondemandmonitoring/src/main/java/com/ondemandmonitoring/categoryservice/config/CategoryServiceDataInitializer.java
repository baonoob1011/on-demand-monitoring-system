package com.ondemandmonitoring.categoryservice.config;

import com.ondemandmonitoring.categoryservice.domain.CategoryService;
import com.ondemandmonitoring.categoryservice.repository.CategoryServiceRepository;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CategoryServiceDataInitializer {

    CategoryServiceRepository categoryServiceRepository;

    @Bean
    ApplicationRunner seedCategoryServices() {
        return args -> DEFAULT_SERVICES.forEach(service -> {
            if (!categoryServiceRepository.existsByNameIgnoreCase(service.name())) {
                categoryServiceRepository.save(CategoryService.builder()
                        .name(service.name())
                        .description(service.description())
                        .build());
            }
        });
    }

    private static final List<ServiceSeed> DEFAULT_SERVICES = List.of(
            new ServiceSeed(
                    "Giám sát công trình",
                    "Theo dõi tiến độ, chụp định kỳ, so sánh theo tuần/tháng và phát hiện khu vực thi công chậm."),
            new ServiceSeed(
                    "Giám sát nông nghiệp",
                    "Kiểm tra cây trồng, vùng thiếu nước, sâu bệnh, stress thực vật và theo dõi diện tích canh tác."),
            new ServiceSeed(
                    "Giám sát khu công nghiệp / nhà máy",
                    "Kiểm tra mái nhà, bồn chứa, khu vực nguy hiểm, hàng rào và tài sản ngoài trời."),
            new ServiceSeed(
                    "Giám sát an ninh khu vực",
                    "Tuần tra theo tuyến, phát hiện người/phương tiện và kiểm tra xâm nhập vùng giới hạn."),
            new ServiceSeed(
                    "Giám sát giao thông",
                    "Theo dõi mật độ xe, ùn tắc, luồng di chuyển và sự cố giao thông."),
            new ServiceSeed(
                    "Giám sát môi trường",
                    "Phát hiện sạt lở, ngập lụt, cháy, thay đổi mặt nước, rác thải hoặc biến động địa hình."),
            new ServiceSeed(
                    "Giám sát điện / hạ tầng",
                    "Kiểm tra đường dây điện, cột điện, trạm biến áp, pin mặt trời và đường ống."),
            new ServiceSeed(
                    "Giám sát kho bãi / logistics",
                    "Giám sát bãi container, bãi xe, khu tập kết vật tư và kiểm kê khu vực ngoài trời."),
            new ServiceSeed(
                    "Giám sát sự kiện / khu đông người",
                    "Quan sát tổng thể khu vực, mật độ người và các điểm bất thường."),
            new ServiceSeed(
                    "Giám sát theo yêu cầu định kỳ",
                    "Khách chọn khu vực và tần suất bay hằng ngày/tuần/tháng để nhận báo cáo tự động.")
    );

    private record ServiceSeed(String name, String description) {
    }
}
