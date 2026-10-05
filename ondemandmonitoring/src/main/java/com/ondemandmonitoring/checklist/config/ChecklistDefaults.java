package com.ondemandmonitoring.checklist.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Version-one bootstrap data, not a policy reconciling administrator-managed templates. */
public final class ChecklistDefaults {
    private ChecklistDefaults() {}

    public static final List<String> CONTENTS = List.of(
            "Kiểm tra tình trạng tổng thể của khu vực",
            "Kiểm tra các dấu hiệu hư hỏng hoặc xuống cấp",
            "Kiểm tra vật cản trong khu vực giám sát",
            "Kiểm tra hàng rào hoặc ranh giới khu vực",
            "Kiểm tra lối ra vào và lối thoát hiểm",
            "Kiểm tra các khu vực có dấu hiệu bất thường",
            "Quan sát và ghi nhận khu vực khó tiếp cận",
            "Kiểm tra tình trạng mái, bề mặt hoặc kết cấu phía trên",
            "Kiểm tra tình trạng đường nội bộ và lối di chuyển",
            "Kiểm tra khu vực phía Bắc",
            "Kiểm tra khu vực phía Nam",
            "Kiểm tra khu vực phía Đông",
            "Kiểm tra khu vực phía Tây",
            "Chụp ảnh tổng quan khu vực giám sát",
            "Chụp ảnh cận cảnh các vị trí bất thường",
            "Ghi nhận các khu vực không thể xác minh",
            "Kiểm tra dấu hiệu xâm nhập hoặc truy cập trái phép",
            "Kiểm tra vật thể hoặc thiết bị xuất hiện bất thường",
            "Kiểm tra khu vực có nguy cơ mất an toàn",
            "Ghi nhận tình trạng hiện trường sau khi hoàn tất kiểm tra");

    public static String code(int number) { return "C%03d".formatted(number); }

    public static Map<String, List<Integer>> templates() {
        var mappings = new LinkedHashMap<String, List<Integer>>();
        mappings.put("Giám sát Kho bãi / Logistics", List.of(1, 3, 4, 5, 9, 17, 18, 19, 14, 20));
        mappings.put("Giám sát Đập nước / Hồ chứa", List.of(1, 2, 6, 7, 15, 19, 14, 20));
        mappings.put("Giám sát Rừng / Điểm nhiệt", List.of(1, 6, 7, 10, 11, 12, 13, 19, 14, 20));
        mappings.put("Giám sát Nông nghiệp / Cây trồng", List.of(1, 6, 7, 10, 11, 12, 13, 14, 15, 20));
        mappings.put("Kiểm tra Sân bay / Đường băng", List.of(1, 2, 3, 9, 17, 18, 19, 14, 20));
        mappings.put("Giám sát Kho công nghiệp / Nhà xưởng", List.of(1, 2, 4, 5, 7, 8, 14, 15, 20));
        mappings.put("Giám sát Mặt nước / Dòng chảy", List.of(1, 6, 7, 15, 19, 14, 20));
        // Observational requirements supplement sensors; they do not claim sensor measurements.
        mappings.put("Đo nhiệt độ / Điểm nhiệt", List.of(1, 6, 7, 15, 16, 19, 14, 20));
        mappings.put("Đo nhiệt độ / Áp suất", List.of(1, 6, 7, 16, 14, 20));
        mappings.put("Kiểm tra Công trình thủy lợi", List.of(1, 2, 7, 8, 9, 15, 19, 20));
        mappings.put("Giám sát Tiến độ Xây dựng", List.of(1, 6, 7, 9, 10, 11, 12, 13, 14, 20));
        mappings.put("Giám sát Sạt lở / Ngập lụt", List.of(1, 2, 3, 6, 7, 9, 15, 19, 14, 20));
        mappings.put("Kiểm tra Tháp viễn thông", List.of(1, 2, 7, 8, 15, 19, 20));
        mappings.put("Giám sát Mục tiêu xa", List.of(1, 6, 7, 16, 14, 20));
        mappings.put("Giám sát Bãi đáp / Trạm drone", List.of(1, 3, 4, 5, 9, 18, 19, 14, 20));
        return mappings;
    }
}
