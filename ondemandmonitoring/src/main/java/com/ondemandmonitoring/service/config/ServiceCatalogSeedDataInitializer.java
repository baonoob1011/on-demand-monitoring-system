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
import java.util.Set;

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
        // Bootstrap only an empty catalog. Existing rows belong to administrators,
        // including renamed, inactive and newly created services.
        if (serviceRepository.count() > 0) {
            log.info("Skipping service catalog bootstrap: catalog already exists");
            return;
        }
        int servicesUpserted = seedServices();
        int legacyServicesCleaned = 0;
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
                        "Giám sát Kho bãi / Logistics",
                        "Giám sát bãi logistics, container, khu bốc xếp, luồng xe ra vào và khu vực lưu trữ ngoài trời bằng drone."
                ),
                new ServiceSeed(
                        "Giám sát Đập nước / Hồ chứa",
                        "Giám sát khu vực đập nước, hồ chứa, cửa xả, thân đập và vùng thượng/hạ lưu; bàn giao ảnh/video hiện trạng và báo cáo kèm hình."
                ),
                new ServiceSeed(
                        "Giám sát Rừng / Điểm nhiệt",
                        "Giám sát khu rừng, thảm thực vật, khu vực tìm kiếm và điểm nhiệt có nguy cơ cháy bằng ảnh/video và dữ liệu nhiệt."
                ),
                new ServiceSeed(
                        "Giám sát Nông nghiệp / Cây trồng",
                        "Giám sát khu canh tác, sức khỏe cây trồng, khu vực phát triển không đồng đều, dấu hiệu khô hạn và bất thường mùa vụ."
                ),
                new ServiceSeed(
                        "Kiểm tra Sân bay / Đường băng",
                        "Kiểm tra đường băng, sân đỗ, khu vực vận hành máy bay và vùng hạn chế để hỗ trợ giám sát an toàn."
                ),
                new ServiceSeed(
                        "Giám sát Kho công nghiệp / Nhà xưởng",
                        "Giám sát kho công nghiệp, mái nhà, bồn chứa, sân bãi và tài sản ngoài trời bằng ảnh/video drone."
                ),
                new ServiceSeed(
                        "Giám sát Mặt nước / Dòng chảy",
                        "Theo dõi mặt nước, dòng chảy và bờ sông/kênh trên bản đồ mô phỏng; bàn giao ảnh/video và báo cáo giám sát."
                ),
                new ServiceSeed(
                        "Đo nhiệt độ / Điểm nhiệt",
                        "Đo nhiệt độ và ghi nhận ảnh nhiệt trong khu vực giám sát; bàn giao ảnh nhiệt và báo cáo phân tích nhiệt."
                ),
                new ServiceSeed(
                        "Đo nhiệt độ / Áp suất",
                        "Theo dõi nhiệt độ và áp suất khí quyển theo khu vực bay, hỗ trợ đánh giá điều kiện môi trường và rủi ro vận hành device."
                ),
                new ServiceSeed(
                        "Kiểm tra Công trình thủy lợi",
                        "Kiểm tra cầu, kè, cống, đường nội bộ, nhà điều hành và hạng mục kỹ thuật quanh khu vực đập/hồ bằng ảnh/video."
                ),
                new ServiceSeed(
                        "Giám sát Tiến độ Xây dựng",
                        "Theo dõi công trình xây dựng, công trường, tiến độ thi công, khu vực làm việc, chụp ảnh định kỳ và đối chiếu hiện trạng bằng ảnh/video."
                ),
                new ServiceSeed(
                        "Giám sát Sạt lở / Ngập lụt",
                        "Giám sát khu vực sạt lở, ngập lụt, tuyến đường bị chặn, dòng chảy bất thường và thay đổi địa hình sau mưa lũ."
                ),
                new ServiceSeed(
                        "Kiểm tra Tháp viễn thông",
                        "Kiểm tra tháp viễn thông, anten, kết cấu cao, thiết bị gắn trên tháp và khu vực xung quanh từ góc nhìn an toàn."
                ),
                new ServiceSeed(
                        "Giám sát Mục tiêu xa",
                        "Giám sát mục tiêu ở khoảng cách xa bằng waypoint, bay vòng quan sát, ghi nhận hiện trạng và kiểm tra khu vực khó tiếp cận."
                ),
                new ServiceSeed(
                        "Giám sát Bãi đáp / Trạm drone",
                        "Giám sát bãi đáp, khu vực cất hạ cánh, điểm quay về, hành lang an toàn và trạng thái khu vực vận hành drone."
                )
        );

        for (ServiceSeed seed : seeds) {
            if (upsertService(seed)) {
                count++;
            }
        }

        return count;
    }

    private void deactivateServicesOutsideMapCatalog(List<ServiceSeed> activeSeeds) {
        List<String> activeNames = activeSeeds.stream()
                .map(ServiceSeed::name)
                .toList();

        for (Service service : serviceRepository.findAll()) {
            if (activeNames.stream().anyMatch(name -> name.equalsIgnoreCase(service.getName()))) {
                continue;
            }
            if (Boolean.TRUE.equals(service.getIsActive())) {
                service.setIsActive(false);
                serviceRepository.save(service);
            }
        }
    }

    private int cleanupLegacyEnglishServices() {
        int count = 0;

        List<LegacyServiceName> legacyNames = List.of(
                new LegacyServiceName("Construction Progress Monitoring", "Giám sát Tiến độ Xây dựng"),
                new LegacyServiceName("Thermal Hotspot Monitoring", "Đo nhiệt độ / Điểm nhiệt"),
                new LegacyServiceName("Water Surface Monitoring", "Giám sát Mặt nước / Dòng chảy"),
                new LegacyServiceName("Warehouse / Logistics Monitoring", "Giám sát Kho bãi / Logistics"),
                new LegacyServiceName("Telecom Tower Inspection", "Kiểm tra Tháp viễn thông"),
                new LegacyServiceName("Agricultural / Crop Monitoring", "Giám sát Nông nghiệp / Cây trồng"),
                new LegacyServiceName("Forest Fire / Thermal Hotspot Monitoring", "Giám sát Rừng / Điểm nhiệt"),
                new LegacyServiceName("Landslide / Flood Monitoring", "Giám sát Sạt lở / Ngập lụt"),
                new LegacyServiceName("Industrial Site / Factory Monitoring", "Giám sát Kho công nghiệp / Nhà xưởng")
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
                new DeliverableTypeSeed("Báo cáo Tiến độ", "PDF"),
                new DeliverableTypeSeed("Báo cáo Nhiệt độ / Áp suất", "PDF")
        );

        for (DeliverableTypeSeed seed : seeds) {
            if (upsertDeliverableType(seed)) {
                count++;
            }
        }
        deactivateUnsupportedDeliverableTypes(seeds);

        return count;
    }

    private int seedServiceDeliverables() {
        int count = 0;

        Map<String, List<String>> mapping = new LinkedHashMap<>();
        mapping.put("Giám sát Kho bãi / Logistics", List.of("Báo cáo Giám sát", "Hình ảnh Kiểm tra", "Video Ghi hình"));
        mapping.put("Giám sát Đập nước / Hồ chứa", List.of("Báo cáo Giám sát", "Hình ảnh Kiểm tra", "Video Ghi hình", "Báo cáo Phân tích Nhiệt"));
        mapping.put("Giám sát Rừng / Điểm nhiệt", List.of("Báo cáo Giám sát", "Hình ảnh Kiểm tra", "Video Ghi hình", "Báo cáo Phân tích Nhiệt"));
        mapping.put("Giám sát Nông nghiệp / Cây trồng", List.of("Báo cáo Giám sát", "Hình ảnh Kiểm tra", "Video Ghi hình"));
        mapping.put("Giám sát Kho công nghiệp / Nhà xưởng", List.of("Báo cáo Giám sát", "Hình ảnh Kiểm tra", "Video Ghi hình", "Báo cáo Phân tích Nhiệt"));
        mapping.put("Giám sát Mặt nước / Dòng chảy", List.of("Báo cáo Giám sát", "Hình ảnh Kiểm tra", "Video Ghi hình"));
        mapping.put("Đo nhiệt độ / Điểm nhiệt", List.of("Báo cáo Phân tích Nhiệt", "Hình ảnh Kiểm tra", "Video Ghi hình"));
        mapping.put("Đo nhiệt độ / Áp suất", List.of("Báo cáo Nhiệt độ / Áp suất", "Báo cáo Giám sát", "Hình ảnh Kiểm tra"));
        mapping.put("Kiểm tra Công trình thủy lợi", List.of("Báo cáo Giám sát", "Hình ảnh Kiểm tra", "Video Ghi hình"));
        mapping.put("Giám sát Tiến độ Xây dựng", List.of("Báo cáo Tiến độ", "Hình ảnh Kiểm tra", "Video Ghi hình"));
        mapping.put("Giám sát Sạt lở / Ngập lụt", List.of("Báo cáo Giám sát", "Hình ảnh Kiểm tra", "Video Ghi hình"));
        mapping.put("Kiểm tra Tháp viễn thông", List.of("Báo cáo Giám sát", "Hình ảnh Kiểm tra", "Video Ghi hình"));

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

        pruneUnsupportedServiceDeliverables();

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
        deactivateLegacySeedSuggestions();
        count += seedGlobalSuggestions();

        Map<String, List<SuggestionSeed>> byServiceName = Map.ofEntries(
                Map.entry("Giám sát Đập nước / Hồ chứa", List.of(
                        new SuggestionSeed("Hình thức giám sát", "Giám sát một lần.", "Tôi muốn giám sát một lần.", 10),
                        new SuggestionSeed("Hình thức giám sát", "Giám sát định kỳ.", "Tôi muốn giám sát định kỳ.", 20),
                        new SuggestionSeed("Kết quả cần nhận", "Nhận ảnh/video.", "Tôi muốn nhận ảnh/video hiện trạng.", 30),
                        new SuggestionSeed("Kết quả cần nhận", "Nhận báo cáo kèm hình.", "Tôi muốn nhận báo cáo kèm hình ảnh.", 40)
                )),
                Map.entry("Giám sát Mặt nước / Dòng chảy", List.of(
                        new SuggestionSeed("Hình thức giám sát", "Giám sát một lần.", "Tôi muốn giám sát một lần.", 10),
                        new SuggestionSeed("Hình thức giám sát", "Giám sát cố định.", "Tôi muốn giám sát cố định khu vực này.", 20),
                        new SuggestionSeed("Kết quả cần nhận", "Nhận ảnh/video.", "Tôi muốn nhận ảnh/video hiện trạng.", 30),
                        new SuggestionSeed("Kết quả cần nhận", "Nhận báo cáo kèm hình.", "Tôi muốn nhận báo cáo kèm hình ảnh.", 40)
                )),
                Map.entry("Đo nhiệt độ / Điểm nhiệt", List.of(
                        new SuggestionSeed("Hình thức giám sát", "Đo một lần.", "Tôi muốn đo một lần.", 10),
                        new SuggestionSeed("Hình thức giám sát", "Đo định kỳ.", "Tôi muốn đo định kỳ.", 20),
                        new SuggestionSeed("Kết quả cần nhận", "Nhận ảnh nhiệt.", "Tôi muốn nhận ảnh nhiệt.", 30),
                        new SuggestionSeed("Kết quả cần nhận", "Nhận báo cáo kèm hình.", "Tôi muốn nhận báo cáo kèm hình ảnh.", 40)
                )),
                Map.entry("Kiểm tra Công trình thủy lợi", List.of(
                        new SuggestionSeed("Hình thức giám sát", "Kiểm tra một lần.", "Tôi muốn kiểm tra một lần.", 10),
                        new SuggestionSeed("Hình thức giám sát", "Kiểm tra định kỳ.", "Tôi muốn kiểm tra định kỳ.", 20),
                        new SuggestionSeed("Kết quả cần nhận", "Nhận ảnh/video.", "Tôi muốn nhận ảnh/video hiện trạng.", 30),
                        new SuggestionSeed("Kết quả cần nhận", "Nhận báo cáo kèm hình.", "Tôi muốn nhận báo cáo kèm hình ảnh.", 40)
                )),
                Map.entry("Giám sát Tiến độ Xây dựng", List.of(
                        new SuggestionSeed("Mục tiêu giám sát", "Theo dõi tiến độ thi công.", "Tôi muốn giám sát tiến độ thi công công trình.", 10),
                        new SuggestionSeed("Mục tiêu giám sát", "Ghi nhận hiện trạng công trường.", "Tôi muốn ghi nhận hình ảnh hiện trạng công trường.", 20),
                        new SuggestionSeed("Tần suất", "Chụp định kỳ.", "Tôi muốn chụp định kỳ để đối chiếu tiến độ.", 30),
                        new SuggestionSeed("Kết quả cần nhận", "Báo cáo tiến độ.", "Tôi muốn nhận báo cáo tiến độ kèm hình ảnh.", 40)
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
                new SuggestionSeed("Hình thức giám sát", "Giám sát một lần.", "Tôi muốn giám sát một lần.", 900),
                new SuggestionSeed("Hình thức giám sát", "Giám sát định kỳ.", "Tôi muốn giám sát định kỳ.", 910),
                new SuggestionSeed("Hình thức giám sát", "Giám sát cố định.", "Tôi muốn giám sát cố định.", 920),
                new SuggestionSeed("Kết quả cần nhận", "Nhận ảnh/video.", "Tôi muốn nhận ảnh/video hiện trạng.", 930),
                new SuggestionSeed("Kết quả cần nhận", "Nhận báo cáo kèm hình.", "Tôi muốn nhận báo cáo kèm hình ảnh.", 940)
        )) {
            if (upsertSuggestion(null, seed)) count++;
        }
        return count;
    }

    private void deactivateLegacySeedSuggestions() {
        suggestionRepository.findAll().stream()
                .filter(row -> "SEED".equalsIgnoreCase(row.getSource()))
                .forEach(row -> {
                    row.setActive(false);
                    suggestionRepository.save(row);
                });
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

    private void deactivateUnsupportedDeliverableTypes(List<DeliverableTypeSeed> supportedSeeds) {
        Set<String> supportedNames = supportedSeeds.stream()
                .map(seed -> seed.name().toLowerCase())
                .collect(java.util.stream.Collectors.toSet());

        deliverableTypeRepository.findAll().stream()
                .filter(type -> type.getName() != null)
                .filter(type -> !supportedNames.contains(type.getName().toLowerCase()))
                .filter(type -> Boolean.TRUE.equals(type.getIsActive()))
                .forEach(type -> {
                    type.setIsActive(false);
                    deliverableTypeRepository.save(type);
                });
    }

    private void pruneUnsupportedServiceDeliverables() {
        serviceDeliverableRepository.findAll().stream()
                .filter(link -> link.getDeliverableType() == null
                        || !Boolean.TRUE.equals(link.getDeliverableType().getIsActive()))
                .forEach(serviceDeliverableRepository::delete);
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
