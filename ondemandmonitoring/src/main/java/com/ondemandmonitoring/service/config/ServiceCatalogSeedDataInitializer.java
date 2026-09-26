package com.ondemandmonitoring.service.config;

import com.ondemandmonitoring.service.domain.DeliverableType;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.domain.ServiceDeliverable;
import com.ondemandmonitoring.service.domain.ServiceRequirementSuggestion;
import com.ondemandmonitoring.service.repository.DeliverableTypeRepository;
import com.ondemandmonitoring.service.repository.ServiceDeliverableRepository;
import com.ondemandmonitoring.service.repository.ServiceRequirementSuggestionRepository;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(20)
@RequiredArgsConstructor
@Slf4j
public class ServiceCatalogSeedDataInitializer implements ApplicationRunner {

    private final ServiceRepository serviceRepository;
    private final DeliverableTypeRepository deliverableTypeRepository;
    private final ServiceDeliverableRepository serviceDeliverableRepository;
    private final ServiceRequirementSuggestionRepository suggestionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int servicesUpserted = seedServices();
        int legacyServicesCleaned = cleanupLegacyEnglishServices();
        int deliverablesUpserted = seedDeliverableTypes();
        int linksCreated = seedServiceDeliverables();
        int suggestionsUpserted = seedRequirementSuggestions();

        log.info(
                "Service catalog seed completed: services={}, legacyServicesCleaned={}, deliverableTypes={}, serviceDeliverables={}, requirementSuggestions={}",
                servicesUpserted,
                legacyServicesCleaned,
                deliverablesUpserted,
                linksCreated,
                suggestionsUpserted
        );
    }

    private int seedServices() {
        int count = 0;

        List<ServiceSeed> seeds = List.of(
                new ServiceSeed(
                        "Giám sát Tòa nhà / Cơ sở hạ tầng",
                        "Kiểm tra tòa nhà, cầu, mái, mặt đứng và hạ tầng khó tiếp cận; phát hiện hư hỏng, nứt vỡ, điểm nóng và rủi ro an toàn."
                ),
                new ServiceSeed(
                        "Giám sát Nông nghiệp / Cây trồng",
                        "Theo dõi sức khỏe cây trồng, vùng thiếu nước, dấu hiệu sâu bệnh, mật độ sinh trưởng và bất thường theo khu vực canh tác."
                ),
                new ServiceSeed(
                        "Giám sát Môi trường",
                        "Theo dõi thay đổi môi trường, ô nhiễm, khu vực ngập, sạt lở, xói mòn và các điểm bất thường cần cảnh báo sớm."
                ),
                new ServiceSeed(
                        "Giám sát Tiến độ Xây dựng",
                        "Ghi nhận hiện trạng công trường, so sánh tiến độ, kiểm tra an toàn lao động và tổng hợp hình ảnh định kỳ."
                ),
                new ServiceSeed(
                        "Kiểm tra Tấm pin Năng lượng Mặt trời",
                        "Kiểm tra bề mặt và ảnh nhiệt hệ thống điện mặt trời để phát hiện điểm nóng, tấm lỗi, bụi bẩn và suy giảm hiệu suất."
                ),
                new ServiceSeed(
                        "Giám sát Cháy rừng / Điểm nhiệt",
                        "Quét khu vực rừng hoặc đất trống để phát hiện khói, điểm nhiệt, vùng khô nguy cơ cao và hỗ trợ phản ứng nhanh."
                ),
                new ServiceSeed(
                        "Giám sát An ninh Khu vực",
                        "Tuần tra khu vực, kiểm tra xâm nhập, theo dõi perimeter, cổng ra vào, kho bãi và các điểm nhạy cảm."
                ),
                new ServiceSeed(
                        "Giám sát Giao thông",
                        "Quan sát lưu lượng, ùn tắc, tai nạn, điểm nghẽn và tình trạng mặt đường tại khu vực cần điều phối."
                ),
                new ServiceSeed(
                        "Khảo sát Bản đồ 2D/3D",
                        "Thu thập dữ liệu ảnh phục vụ orthomosaic, bản đồ hiện trạng, mô hình 3D, point cloud và đo đạc khu vực."
                ),
                new ServiceSeed(
                        "Kiểm tra Đường dây Điện / Trạm biến áp",
                        "Kiểm tra tuyến điện, cột, sứ, dây dẫn, hành lang an toàn và điểm nhiệt tại thiết bị điện."
                ),
                new ServiceSeed(
                        "Giám sát Kho bãi / Logistics",
                        "Theo dõi bãi hàng, container, luồng xe, tồn kho ngoài trời, an toàn vận hành và khu vực bốc dỡ."
                ),
                new ServiceSeed(
                        "Giám sát Sạt lở / Ngập lụt",
                        "Quan sát vùng nguy cơ sạt lở, mực nước, dòng chảy, điểm ngập và thay đổi địa hình sau mưa bão."
                ),
                new ServiceSeed(
                        "Giám sát Sự kiện / Đám đông",
                        "Theo dõi mật độ đám đông, lối ra vào, khu vực quá tải và hỗ trợ điều phối an ninh sự kiện."
                ),
                new ServiceSeed(
                        "Kiểm tra Đường ống / Hạ tầng tuyến tính",
                        "Giám sát tuyến ống, kênh mương, hành lang kỹ thuật, rò rỉ, sụt lún và vật cản trên tuyến."
                ),
                new ServiceSeed(
                        "Giám sát Công trình Cầu / Đường",
                        "Kiểm tra mặt đường, cầu, taluy, biển báo, lan can, mố trụ và các hư hỏng ảnh hưởng giao thông."
                ),
                new ServiceSeed(
                        "Kiểm kê Tài sản Ngoài trời",
                        "Đếm và ghi nhận tài sản ngoài trời như xe, thiết bị, vật tư, container, trụ đèn hoặc hạng mục phân tán."
                ),
                new ServiceSeed(
                        "Giám sát Khu công nghiệp / Nhà máy",
                        "Quan sát khu sản xuất, mái nhà xưởng, bãi vật liệu, tuyến nội bộ, an toàn vận hành và điểm bất thường."
                ),
                new ServiceSeed(
                        "Giám sát Bờ biển / Mặt nước",
                        "Theo dõi bờ biển, sông hồ, tàu thuyền, rác nổi, xói lở bờ và tình trạng mặt nước."
                )
        );

        for (ServiceSeed seed : seeds) {
            if (upsertService(seed)) {
                count++;
            }
        }

        return count;
    }

    private int cleanupLegacyEnglishServices() {
        int count = 0;

        List<LegacyServiceName> legacyNames = List.of(
                new LegacyServiceName("Building / Infrastructure Monitoring", "Giám sát Tòa nhà / Cơ sở hạ tầng"),
                new LegacyServiceName("Agricultural / Crop Monitoring", "Giám sát Nông nghiệp / Cây trồng"),
                new LegacyServiceName("Environmental Monitoring", "Giám sát Môi trường"),
                new LegacyServiceName("Construction Progress Monitoring", "Giám sát Tiến độ Xây dựng"),
                new LegacyServiceName("Solar Panel Inspection", "Kiểm tra Tấm pin Năng lượng Mặt trời"),
                new LegacyServiceName("Security Area Patrol", "Giám sát An ninh Khu vực"),
                new LegacyServiceName("Traffic and Event Monitoring", "Giám sát Giao thông"),
                new LegacyServiceName("Traffic / Event Monitoring", "Giám sát Giao thông"),
                new LegacyServiceName("2D/3D Mapping (Orthomosaic)", "Khảo sát Bản đồ 2D/3D"),
                new LegacyServiceName("2D/3D Mapping", "Khảo sát Bản đồ 2D/3D"),
                new LegacyServiceName("Forest Fire / Thermal Hotspot Monitoring", "Giám sát Cháy rừng / Điểm nhiệt"),
                new LegacyServiceName("Power Line / Substation Inspection", "Kiểm tra Đường dây Điện / Trạm biến áp"),
                new LegacyServiceName("Warehouse / Logistics Monitoring", "Giám sát Kho bãi / Logistics"),
                new LegacyServiceName("Landslide / Flood Monitoring", "Giám sát Sạt lở / Ngập lụt"),
                new LegacyServiceName("Crowd / Event Monitoring", "Giám sát Sự kiện / Đám đông"),
                new LegacyServiceName("Pipeline / Linear Infrastructure Inspection", "Kiểm tra Đường ống / Hạ tầng tuyến tính"),
                new LegacyServiceName("Bridge / Roadwork Monitoring", "Giám sát Công trình Cầu / Đường"),
                new LegacyServiceName("Outdoor Asset Inventory", "Kiểm kê Tài sản Ngoài trời"),
                new LegacyServiceName("Industrial Site / Factory Monitoring", "Giám sát Khu công nghiệp / Nhà máy"),
                new LegacyServiceName("Coastal / Water Surface Monitoring", "Giám sát Bờ biển / Mặt nước")
        );

        for (LegacyServiceName legacyName : legacyNames) {
            Service legacy = serviceRepository.findByNameIgnoreCase(legacyName.englishName()).orElse(null);

            if (legacy == null) {
                continue;
            }

            Service vietnamese = serviceRepository.findByNameIgnoreCase(legacyName.vietnameseName()).orElse(null);

            if (vietnamese == null) {
                legacy.setName(legacyName.vietnameseName());
                legacy.setIsActive(true);
                serviceRepository.save(legacy);
            } else if (!Boolean.FALSE.equals(legacy.getIsActive())) {
                deleteOrDeactivateLegacyService(legacy);
            } else {
                continue;
            }

            count++;
        }

        for (Service service : serviceRepository.findAll()) {
            if (!isLegacyEnglishService(service)) {
                continue;
            }

            deleteOrDeactivateLegacyService(service);
            count++;
        }

        return count;
    }

    private void deleteOrDeactivateLegacyService(Service service) {
        List<ServiceDeliverable> links = serviceDeliverableRepository.findAllByServiceId(service.getId());
        serviceDeliverableRepository.deleteAll(links);

        try {
            serviceRepository.delete(service);
            serviceRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            service.setIsActive(false);
            serviceRepository.save(service);
        }
    }

    private boolean isLegacyEnglishService(Service service) {
        if (!Boolean.TRUE.equals(service.getIsActive())) {
            return false;
        }

        String name = service.getName() == null ? "" : service.getName();
        String description = service.getDescription() == null ? "" : service.getDescription();
        String text = (name + " " + description).toLowerCase();

        return text.contains("monitoring")
                || text.contains("inspection")
                || text.contains("infrastructure")
                || text.contains("agricultural")
                || text.contains("environmental")
                || text.contains("construction")
                || text.contains("solar panel")
                || text.contains("thermal anomaly")
                || text.contains("facade")
                || text.contains("alignment with plans")
                || text.contains("crop health");
    }

    private int seedDeliverableTypes() {
        int count = 0;

        List<DeliverableTypeSeed> seeds = List.of(
                new DeliverableTypeSeed("Báo cáo Giám sát", "PDF"),
                new DeliverableTypeSeed("Hình ảnh Kiểm tra", "JPG"),
                new DeliverableTypeSeed("Video Ghi hình", "MP4"),
                new DeliverableTypeSeed("Báo cáo Phân tích Nhiệt", "PDF"),
                new DeliverableTypeSeed("Báo cáo Bất thường", "PDF"),
                new DeliverableTypeSeed("Báo cáo Tiến độ", "PDF"),
                new DeliverableTypeSeed("Bản đồ Khu vực", "GeoJSON"),
                new DeliverableTypeSeed("Orthomosaic 2D", "GeoTIFF"),
                new DeliverableTypeSeed("Mô hình 3D / Point Cloud", "LAS/OBJ"),
                new DeliverableTypeSeed("Báo cáo NDVI / Sức khỏe cây trồng", "PDF"),
                new DeliverableTypeSeed("Livestream Giám sát", "HLS"),
                new DeliverableTypeSeed("Nhật ký Sự kiện", "CSV")
        );

        for (DeliverableTypeSeed seed : seeds) {
            if (upsertDeliverableType(seed)) {
                count++;
            }
        }

        return count;
    }

    private int seedServiceDeliverables() {
        int count = 0;

        Map<String, List<String>> mapping = new LinkedHashMap<>();
        mapping.put("Giám sát Tòa nhà / Cơ sở hạ tầng", List.of("Báo cáo Giám sát", "Hình ảnh Kiểm tra", "Video Ghi hình", "Báo cáo Phân tích Nhiệt", "Báo cáo Bất thường", "Mô hình 3D / Point Cloud"));
        mapping.put("Giám sát Nông nghiệp / Cây trồng", List.of("Báo cáo Giám sát", "Bản đồ Khu vực", "Orthomosaic 2D", "Báo cáo NDVI / Sức khỏe cây trồng", "Hình ảnh Kiểm tra", "Báo cáo Bất thường"));
        mapping.put("Giám sát Môi trường", List.of("Báo cáo Giám sát", "Bản đồ Khu vực", "Video Ghi hình", "Báo cáo Phân tích Nhiệt", "Báo cáo Bất thường"));
        mapping.put("Giám sát Tiến độ Xây dựng", List.of("Báo cáo Tiến độ", "Hình ảnh Kiểm tra", "Video Ghi hình", "Orthomosaic 2D", "Mô hình 3D / Point Cloud"));
        mapping.put("Kiểm tra Tấm pin Năng lượng Mặt trời", List.of("Báo cáo Phân tích Nhiệt", "Hình ảnh Kiểm tra", "Báo cáo Bất thường", "Báo cáo Giám sát"));
        mapping.put("Giám sát Cháy rừng / Điểm nhiệt", List.of("Báo cáo Phân tích Nhiệt", "Livestream Giám sát", "Báo cáo Bất thường", "Bản đồ Khu vực", "Video Ghi hình"));
        mapping.put("Giám sát An ninh Khu vực", List.of("Livestream Giám sát", "Video Ghi hình", "Nhật ký Sự kiện", "Báo cáo Bất thường", "Hình ảnh Kiểm tra"));
        mapping.put("Giám sát Giao thông", List.of("Video Ghi hình", "Báo cáo Giám sát", "Nhật ký Sự kiện", "Bản đồ Khu vực"));
        mapping.put("Khảo sát Bản đồ 2D/3D", List.of("Bản đồ Khu vực", "Orthomosaic 2D", "Mô hình 3D / Point Cloud", "Hình ảnh Kiểm tra"));
        mapping.put("Kiểm tra Đường dây Điện / Trạm biến áp", List.of("Báo cáo Giám sát", "Hình ảnh Kiểm tra", "Báo cáo Phân tích Nhiệt", "Báo cáo Bất thường", "Video Ghi hình"));
        mapping.put("Giám sát Kho bãi / Logistics", List.of("Báo cáo Giám sát", "Video Ghi hình", "Nhật ký Sự kiện", "Hình ảnh Kiểm tra", "Bản đồ Khu vực"));
        mapping.put("Giám sát Sạt lở / Ngập lụt", List.of("Báo cáo Giám sát", "Bản đồ Khu vực", "Orthomosaic 2D", "Video Ghi hình", "Báo cáo Bất thường"));
        mapping.put("Giám sát Sự kiện / Đám đông", List.of("Livestream Giám sát", "Video Ghi hình", "Nhật ký Sự kiện", "Báo cáo Bất thường"));
        mapping.put("Kiểm tra Đường ống / Hạ tầng tuyến tính", List.of("Báo cáo Giám sát", "Hình ảnh Kiểm tra", "Video Ghi hình", "Bản đồ Khu vực", "Báo cáo Bất thường"));
        mapping.put("Giám sát Công trình Cầu / Đường", List.of("Báo cáo Giám sát", "Hình ảnh Kiểm tra", "Video Ghi hình", "Báo cáo Bất thường", "Mô hình 3D / Point Cloud"));
        mapping.put("Kiểm kê Tài sản Ngoài trời", List.of("Báo cáo Giám sát", "Hình ảnh Kiểm tra", "Nhật ký Sự kiện", "Bản đồ Khu vực"));
        mapping.put("Giám sát Khu công nghiệp / Nhà máy", List.of("Báo cáo Giám sát", "Video Ghi hình", "Báo cáo Phân tích Nhiệt", "Báo cáo Bất thường", "Hình ảnh Kiểm tra"));
        mapping.put("Giám sát Bờ biển / Mặt nước", List.of("Báo cáo Giám sát", "Video Ghi hình", "Bản đồ Khu vực", "Orthomosaic 2D", "Báo cáo Bất thường"));

        for (Map.Entry<String, List<String>> entry : mapping.entrySet()) {
            Service service = serviceRepository.findByNameIgnoreCase(entry.getKey()).orElse(null);

            if (service == null) {
                continue;
            }

            for (String deliverableName : entry.getValue()) {
                DeliverableType deliverableType = deliverableTypeRepository
                        .findByNameIgnoreCase(deliverableName)
                        .orElse(null);

                if (deliverableType == null || serviceDeliverableRepository
                        .existsByServiceIdAndDeliverableTypeId(service.getId(), deliverableType.getId())) {
                    continue;
                }

                serviceDeliverableRepository.save(
                        ServiceDeliverable.builder()
                                .service(service)
                                .deliverableType(deliverableType)
                                .build()
                );
                count++;
            }
        }

        return count;
    }

    private boolean upsertService(ServiceSeed seed) {
        return serviceRepository.findByNameIgnoreCase(seed.name())
                .map(existing -> {
                    boolean changed = false;

                    if (!seed.description().equals(existing.getDescription())) {
                        existing.setDescription(seed.description());
                        changed = true;
                    }

                    if (!Boolean.TRUE.equals(existing.getIsActive())) {
                        existing.setIsActive(true);
                        changed = true;
                    }

                    if (changed) {
                        serviceRepository.save(existing);
                    }

                    return changed;
                })
                .orElseGet(() -> {
                    serviceRepository.save(Service.builder()
                            .name(seed.name())
                            .description(seed.description())
                            .isActive(true)
                            .build());
                    return true;
                });
    }

    private int seedRequirementSuggestions() {
        int count = 0;
        count += seedGlobalSuggestions();

        Map<String, List<SuggestionSeed>> byServiceName = Map.ofEntries(
                Map.entry("Giám sát Tòa nhà / Cơ sở hạ tầng", List.of(
                        new SuggestionSeed("Mục tiêu AI check", "AI check nứt vỡ/hư hỏng.", "AI check nứt vỡ/hư hỏng.", 10),
                        new SuggestionSeed("Mục tiêu AI check", "Theo dõi tiến độ công trình.", "Theo dõi tiến độ công trình.", 20),
                        new SuggestionSeed("Khu vực ưu tiên", "Ưu tiên mặt đứng và mặt tiền.", "Ưu tiên mặt đứng và mặt tiền.", 30),
                        new SuggestionSeed("Khu vực ưu tiên", "Ưu tiên mái và khu kỹ thuật.", "Ưu tiên mái và khu kỹ thuật.", 40)
                )),
                Map.entry("Giám sát Nông nghiệp / Cây trồng", List.of(
                        new SuggestionSeed("Mục tiêu AI check", "Phát hiện cây sinh trưởng kém.", "Phát hiện cây sinh trưởng kém.", 10),
                        new SuggestionSeed("Mục tiêu AI check", "Tìm vùng thiếu nước.", "Tìm vùng thiếu nước.", 20),
                        new SuggestionSeed("Mục tiêu AI check", "Phát hiện sâu bệnh.", "Phát hiện sâu bệnh.", 30),
                        new SuggestionSeed("Thông báo & tần suất", "Theo dõi định kỳ để so sánh thay đổi.", "Theo dõi định kỳ để so sánh thay đổi.", 40)
                )),
                Map.entry("Giám sát Cháy rừng / Điểm nhiệt", List.of(
                        new SuggestionSeed("Mục tiêu AI check", "Phát hiện khói/điểm nhiệt.", "Phát hiện khói/điểm nhiệt.", 10),
                        new SuggestionSeed("Mục tiêu AI check", "Cảnh báo cháy sớm.", "Cảnh báo cháy sớm.", 20),
                        new SuggestionSeed("Kết quả cần nhận", "Bản đồ nguy cơ cháy.", "Bản đồ nguy cơ cháy.", 30),
                        new SuggestionSeed("Thông báo & tần suất", "Báo khẩn khi có điểm nhiệt.", "Báo khẩn khi có điểm nhiệt.", 40)
                )),
                Map.entry("Khảo sát Bản đồ 2D/3D", List.of(
                        new SuggestionSeed("Sản phẩm bản đồ", "Tôi cần orthomosaic 2D.", "Tôi cần orthomosaic 2D.", 10),
                        new SuggestionSeed("Sản phẩm bản đồ", "Tôi cần mô hình 3D/point cloud.", "Tôi cần mô hình 3D/point cloud.", 20),
                        new SuggestionSeed("Sản phẩm bản đồ", "Tôi muốn đo diện tích/thể tích.", "Tôi muốn đo diện tích/thể tích.", 30),
                        new SuggestionSeed("Độ chi tiết", "Ưu tiên ảnh độ phân giải cao.", "Ưu tiên ảnh độ phân giải cao.", 40)
                )),
                Map.entry("Giám sát Kho bãi / Logistics", List.of(
                        new SuggestionSeed("Mục tiêu AI check", "Kiểm kê container/xe/vật tư.", "Kiểm kê container/xe/vật tư.", 10),
                        new SuggestionSeed("Mục tiêu AI check", "Phát hiện khu vực quá tải.", "Phát hiện khu vực quá tải.", 20),
                        new SuggestionSeed("Mục tiêu AI check", "Theo dõi luồng ra vào.", "Theo dõi luồng ra vào.", 30),
                        new SuggestionSeed("Kết quả cần nhận", "Báo cáo số lượng và vị trí.", "Báo cáo số lượng và vị trí.", 40)
                )),
                Map.entry("Giám sát Sự kiện / Đám đông", List.of(
                        new SuggestionSeed("Mục tiêu AI check", "Theo dõi mật độ đám đông.", "Theo dõi mật độ đám đông.", 10),
                        new SuggestionSeed("Mục tiêu AI check", "Phát hiện điểm ùn ứ.", "Phát hiện điểm ùn ứ.", 20),
                        new SuggestionSeed("Mục tiêu AI check", "Giám sát bãi đỗ xe.", "Giám sát bãi đỗ xe.", 30),
                        new SuggestionSeed("Thông báo & tần suất", "Cảnh báo thời gian thực.", "Cảnh báo thời gian thực.", 40)
                ))
        );

        for (Map.Entry<String, List<SuggestionSeed>> entry : byServiceName.entrySet()) {
            Service service = serviceRepository.findByNameIgnoreCase(entry.getKey()).orElse(null);
            if (service == null) continue;
            for (SuggestionSeed seed : entry.getValue()) {
                if (upsertSuggestion(service, seed)) count++;
            }
        }

        return count;
    }

    private int seedGlobalSuggestions() {
        int count = 0;
        for (SuggestionSeed seed : List.of(
                new SuggestionSeed("Mục tiêu AI check", "Phát hiện bất thường chính.", "Phát hiện bất thường chính.", 900),
                new SuggestionSeed("Kết quả cần nhận", "Ảnh/video minh chứng.", "Tôi muốn ảnh/video minh chứng.", 910),
                new SuggestionSeed("Kết quả cần nhận", "Đánh dấu vị trí trên bản đồ.", "Đánh dấu vị trí trên bản đồ.", 920),
                new SuggestionSeed("Thông báo & tần suất", "Báo khẩn qua email/điện thoại.", "Báo khẩn qua email/điện thoại khi phát hiện bất thường.", 930),
                new SuggestionSeed("Thông báo & tần suất", "Chỉ tổng hợp trong báo cáo.", "Chỉ tổng hợp trong báo cáo sau chuyến bay.", 940)
        )) {
            if (upsertSuggestion(null, seed)) count++;
        }
        return count;
    }

    private boolean upsertSuggestion(Service service, SuggestionSeed seed) {
        ServiceRequirementSuggestion suggestion = service == null
                ? suggestionRepository.findByActiveTrueAndServiceIsNullOrderBySortOrderAscCreatedAtAsc().stream()
                        .filter(row -> row.getCategory().equalsIgnoreCase(seed.category())
                                && row.getLabel().equalsIgnoreCase(seed.label()))
                        .findFirst()
                        .orElse(null)
                : suggestionRepository
                        .findByServiceIdAndCategoryIgnoreCaseAndLabelIgnoreCase(service.getId(), seed.category(), seed.label())
                        .orElse(null);

        if (suggestion == null) {
            suggestion = new ServiceRequirementSuggestion();
            suggestion.setService(service);
            suggestion.setCategory(seed.category());
            suggestion.setLabel(seed.label());
        }

        boolean changed = false;
        if (!seed.message().equals(suggestion.getMessage())) {
            suggestion.setMessage(seed.message());
            changed = true;
        }
        if (!seed.sortOrder().equals(suggestion.getSortOrder())) {
            suggestion.setSortOrder(seed.sortOrder());
            changed = true;
        }
        if (!Boolean.TRUE.equals(suggestion.getActive())) {
            suggestion.setActive(true);
            changed = true;
        }
        if (!"SEED".equals(suggestion.getSource())) {
            suggestion.setSource("SEED");
            changed = true;
        }

        if (suggestion.getId() == null || changed) {
            suggestionRepository.save(suggestion);
            return true;
        }
        return false;
    }

    private boolean upsertDeliverableType(DeliverableTypeSeed seed) {
        return deliverableTypeRepository.findByNameIgnoreCase(seed.name())
                .map(existing -> {
                    boolean changed = false;

                    if (!seed.defaultFormat().equals(existing.getDefaultFormat())) {
                        existing.setDefaultFormat(seed.defaultFormat());
                        changed = true;
                    }

                    if (!Boolean.TRUE.equals(existing.getIsActive())) {
                        existing.setIsActive(true);
                        changed = true;
                    }

                    if (changed) {
                        deliverableTypeRepository.save(existing);
                    }

                    return changed;
                })
                .orElseGet(() -> {
                    deliverableTypeRepository.save(DeliverableType.builder()
                            .name(seed.name())
                            .defaultFormat(seed.defaultFormat())
                            .isActive(true)
                            .build());
                    return true;
                });
    }

    private record ServiceSeed(String name, String description) {
    }

    private record LegacyServiceName(String englishName, String vietnameseName) {
    }

    private record DeliverableTypeSeed(String name, String defaultFormat) {
    }

    private record SuggestionSeed(String category, String label, String message, Integer sortOrder) {
    }
}
