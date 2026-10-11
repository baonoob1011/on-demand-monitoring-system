package com.ondemandmonitoring.service.config;

import com.ondemandmonitoring.service.domain.DeliverableType;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.domain.ServiceDeliverable;
import com.ondemandmonitoring.service.domain.ServiceRequirementSuggestion;
import com.ondemandmonitoring.service.repository.DeliverableTypeRepository;
import com.ondemandmonitoring.service.repository.ServiceDeliverableRepository;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import com.ondemandmonitoring.service.repository.ServiceRequirementSuggestionRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
@RequiredArgsConstructor
@Slf4j
public class ServiceCatalogSeedDataInitializer implements ApplicationRunner {

    private static final String REQUIREMENT_SOURCE = "SEED";

    private final ServiceRepository serviceRepository;
    private final DeliverableTypeRepository deliverableTypeRepository;
    private final ServiceDeliverableRepository serviceDeliverableRepository;
    private final ServiceRequirementSuggestionRepository suggestionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int servicesUpserted = seedServices();
        int legacyServicesDeactivated = deactivateLegacyServices();
        int deliverablesUpserted = seedDeliverableTypes();
        int legacyDeliverablesDeactivated = deactivateUnsupportedDeliverableTypes();
        int linksCreated = seedServiceDeliverables();
        pruneUnsupportedServiceDeliverables();
        int suggestionsUpserted = seedRequirementSuggestions();

        log.info(
                "Service catalog seed completed: services={}, legacyServicesDeactivated={}, deliverableTypes={}, legacyDeliverableTypesDeactivated={}, serviceDeliverables={}, requirementSuggestions={}",
                servicesUpserted,
                legacyServicesDeactivated,
                deliverablesUpserted,
                legacyDeliverablesDeactivated,
                linksCreated,
                suggestionsUpserted
        );
    }

    private int seedServices() {
        int count = 0;
        for (ServiceSeed seed : serviceSeeds()) {
            if (upsertService(seed)) {
                count++;
            }
        }
        return count;
    }

    private List<ServiceSeed> serviceSeeds() {
        return List.of(
                new ServiceSeed(
                        "CONSTRUCTION_MONITORING",
                        "Giám sát công trình",
                        """
                        Chụp ảnh và video hiện trạng công trình, hỗ trợ theo dõi và đối chiếu
                        tiến độ thi công theo từng thời điểm.
                        """.strip(),
                        3_500_000L
                ),
                new ServiceSeed(
                        "FACTORY_INSPECTION",
                        "Kiểm tra nhà xưởng",
                        """
                        Quan sát mái, bề mặt và các khu vực khó tiếp cận của nhà xưởng bằng
                        hình ảnh và video từ drone.
                        """.strip(),
                        3_000_000L
                ),
                new ServiceSeed(
                        "AREA_MONITORING",
                        "Giám sát khu vực",
                        """
                        Chụp ảnh và video tổng quan một khu vực theo vị trí và phạm vi giám sát
                        do khách hàng yêu cầu.
                        """.strip(),
                        2_500_000L
                ),
                new ServiceSeed(
                        "FOREST_MONITORING",
                        "Giám sát rừng",
                        """
                        Chụp ảnh và video khu vực rừng, ghi nhận hiện trạng và hỗ trợ quan sát
                        các khu vực có dấu hiệu bất thường.
                        """.strip(),
                        4_000_000L
                )
        );
    }

    private boolean upsertService(ServiceSeed seed) {
        Service service = serviceRepository.findByCodeIgnoreCase(seed.code())
                .or(() -> serviceRepository.findByNameIgnoreCase(seed.name()))
                .orElseGet(Service::new);

        boolean changed = false;
        if (!seed.code().equals(service.getCode())) {
            service.setCode(seed.code());
            changed = true;
        }
        if (!seed.name().equals(service.getName())) {
            service.setName(seed.name());
            changed = true;
        }
        if (!seed.description().equals(service.getDescription())) {
            service.setDescription(seed.description());
            changed = true;
        }
        BigDecimal price = BigDecimal.valueOf(seed.basePrice());
        if (!price.equals(service.getBasePrice())) {
            service.setBasePrice(price);
            changed = true;
        }
        if (!Boolean.TRUE.equals(service.getIsActive())) {
            service.setIsActive(true);
            changed = true;
        }

        if (service.getId() == null || changed) {
            serviceRepository.save(service);
            return true;
        }
        return false;
    }

    private int deactivateLegacyServices() {
        Set<String> supportedCodes = serviceSeeds().stream()
                .map(ServiceSeed::code)
                .collect(Collectors.toSet());
        Set<String> supportedNames = serviceSeeds().stream()
                .map(ServiceSeed::name)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        int count = 0;
        for (Service service : serviceRepository.findAll()) {
            boolean supported = supportedCodes.contains(service.getCode())
                    || (service.getName() != null && supportedNames.contains(service.getName().toLowerCase()));
            if (!supported && Boolean.TRUE.equals(service.getIsActive())) {
                service.setIsActive(false);
                serviceRepository.save(service);
                count++;
            }
        }
        return count;
    }

    private int seedDeliverableTypes() {
        int count = 0;
        for (DeliverableTypeSeed seed : deliverableSeeds()) {
            if (upsertDeliverableType(seed)) {
                count++;
            }
        }
        return count;
    }

    private List<DeliverableTypeSeed> deliverableSeeds() {
        return List.of(
                new DeliverableTypeSeed("Ảnh chụp", "IMAGE"),
                new DeliverableTypeSeed("Video", "VIDEO"),
                new DeliverableTypeSeed("Báo cáo kết quả", "REPORT")
        );
    }

    private boolean upsertDeliverableType(DeliverableTypeSeed seed) {
        DeliverableType type = deliverableTypeRepository.findByNameIgnoreCase(seed.name())
                .orElseGet(DeliverableType::new);

        boolean changed = false;
        if (!seed.name().equals(type.getName())) {
            type.setName(seed.name());
            changed = true;
        }
        if (!seed.defaultFormat().equals(type.getDefaultFormat())) {
            type.setDefaultFormat(seed.defaultFormat());
            changed = true;
        }
        if (!Boolean.TRUE.equals(type.getIsActive())) {
            type.setIsActive(true);
            changed = true;
        }

        if (type.getId() == null || changed) {
            deliverableTypeRepository.save(type);
            return true;
        }
        return false;
    }

    private int deactivateUnsupportedDeliverableTypes() {
        Set<String> supportedNames = deliverableSeeds().stream()
                .map(DeliverableTypeSeed::name)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        int count = 0;
        for (DeliverableType type : deliverableTypeRepository.findAll()) {
            if (type.getName() == null || supportedNames.contains(type.getName().toLowerCase())) {
                continue;
            }
            if (Boolean.TRUE.equals(type.getIsActive())) {
                type.setIsActive(false);
                deliverableTypeRepository.save(type);
                count++;
            }
        }
        return count;
    }

    private int seedServiceDeliverables() {
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        mapping.put("CONSTRUCTION_MONITORING", List.of("Ảnh chụp", "Video", "Báo cáo kết quả"));
        mapping.put("FACTORY_INSPECTION", List.of("Ảnh chụp", "Video", "Báo cáo kết quả"));
        mapping.put("AREA_MONITORING", List.of("Ảnh chụp", "Video"));
        mapping.put("FOREST_MONITORING", List.of("Ảnh chụp", "Video", "Báo cáo kết quả"));

        int count = 0;
        for (Map.Entry<String, List<String>> entry : mapping.entrySet()) {
            Service service = serviceRepository.findByCodeIgnoreCase(entry.getKey()).orElse(null);
            if (service == null) {
                continue;
            }
            for (String deliverableName : entry.getValue()) {
                DeliverableType deliverableType = deliverableTypeRepository
                        .findByNameIgnoreCase(deliverableName)
                        .orElse(null);
                if (deliverableType == null
                        || !Boolean.TRUE.equals(deliverableType.getIsActive())
                        || serviceDeliverableRepository.existsByServiceIdAndDeliverableTypeId(
                                service.getId(), deliverableType.getId())) {
                    continue;
                }
                serviceDeliverableRepository.save(ServiceDeliverable.builder()
                        .service(service)
                        .deliverableType(deliverableType)
                        .build());
                count++;
            }
        }
        return count;
    }

    private void pruneUnsupportedServiceDeliverables() {
        Set<String> supportedServiceCodes = serviceSeeds().stream()
                .map(ServiceSeed::code)
                .collect(Collectors.toSet());
        Set<String> supportedDeliverableNames = deliverableSeeds().stream()
                .map(DeliverableTypeSeed::name)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        serviceDeliverableRepository.findAll().stream()
                .filter(link -> link.getService() == null
                        || link.getDeliverableType() == null
                        || !supportedServiceCodes.contains(link.getService().getCode())
                        || link.getDeliverableType().getName() == null
                        || !supportedDeliverableNames.contains(link.getDeliverableType().getName().toLowerCase()))
                .forEach(serviceDeliverableRepository::delete);
    }

    private int seedRequirementSuggestions() {
        deactivateLegacySeedSuggestions();
        int count = 0;
        for (ServiceSeed serviceSeed : serviceSeeds()) {
            Service service = serviceRepository.findByCodeIgnoreCase(serviceSeed.code()).orElse(null);
            if (service == null) {
                continue;
            }
            for (SuggestionSeed suggestionSeed : suggestionsByServiceCode().get(serviceSeed.code())) {
                if (upsertSuggestion(service, suggestionSeed)) {
                    count++;
                }
            }
        }
        return count;
    }

    private Map<String, List<SuggestionSeed>> suggestionsByServiceCode() {
        return Map.of(
                "CONSTRUCTION_MONITORING", List.of(
                        requirement("Kiểm tra tình trạng tổng thể công trình",
                                "Ghi nhận hình ảnh tổng quan hiện trạng công trình.", 10),
                        requirement("Ghi nhận tiến độ các khu vực đang thi công",
                                "Chụp ảnh/video các khu vực đang được triển khai.", 20),
                        requirement("Kiểm tra mặt ngoài công trình",
                                "Ghi nhận mặt ngoài, bề mặt và kết cấu có thể quan sát bằng drone.", 30),
                        requirement("Quan sát khu vực khó tiếp cận",
                                "Ghi nhận các khu vực trên cao hoặc khó quan sát từ mặt đất.", 40),
                        requirement("Chụp ảnh tổng quan công trình",
                                "Chụp ảnh toàn cảnh khu vực công trình.", 50),
                        requirement("Ghi nhận hiện trạng sau khi hoàn tất kiểm tra",
                                "Lưu lại hình ảnh/video hiện trạng tại thời điểm thực hiện mission.", 60)
                ),
                "FACTORY_INSPECTION", List.of(
                        requirement("Kiểm tra tình trạng tổng thể nhà xưởng",
                                "Ghi nhận toàn cảnh khu vực nhà xưởng.", 10),
                        requirement("Kiểm tra mái nhà xưởng",
                                "Chụp ảnh/video bề mặt mái có thể quan sát từ trên cao.", 20),
                        requirement("Kiểm tra bề mặt và kết cấu phía trên",
                                "Ghi nhận các khu vực kết cấu có thể quan sát bằng drone.", 30),
                        requirement("Quan sát khu vực khó tiếp cận",
                                "Ghi nhận các vị trí khó kiểm tra trực tiếp từ mặt đất.", 40),
                        requirement("Chụp ảnh các vị trí bất thường",
                                "Ghi nhận cận cảnh những vị trí cần chú ý nếu quan sát thấy.", 50),
                        requirement("Ghi nhận hiện trạng sau khi hoàn tất kiểm tra",
                                "Lưu ảnh/video tổng kết hiện trạng nhà xưởng.", 60)
                ),
                "AREA_MONITORING", List.of(
                        requirement("Ghi nhận toàn cảnh khu vực",
                                "Chụp ảnh tổng quan phạm vi giám sát.", 10),
                        requirement("Ghi nhận các khu vực chính",
                                "Chụp ảnh/video các vị trí chính trong phạm vi yêu cầu.", 20),
                        requirement("Quan sát khu vực khó tiếp cận",
                                "Ghi nhận các khu vực khó quan sát trực tiếp từ mặt đất.", 30),
                        requirement("Chụp ảnh các vị trí khách hàng yêu cầu",
                                "Ghi nhận chi tiết các vị trí cụ thể được khách hàng chỉ định.", 40),
                        requirement("Ghi nhận hiện trạng khu vực",
                                "Lưu ảnh/video thể hiện tình trạng khu vực tại thời điểm mission.", 50)
                ),
                "FOREST_MONITORING", List.of(
                        requirement("Ghi nhận toàn cảnh khu vực rừng",
                                "Chụp ảnh/video tổng quan phạm vi rừng cần giám sát.", 10),
                        requirement("Quan sát tình trạng khu vực cây xanh",
                                "Ghi nhận hình ảnh hiện trạng các khu vực cây xanh từ trên cao.", 20),
                        requirement("Quan sát khu vực có dấu hiệu bất thường",
                                "Ghi nhận hình ảnh các khu vực khác biệt hoặc cần kiểm tra thêm.", 30),
                        requirement("Quan sát khu vực khó tiếp cận",
                                "Sử dụng drone ghi nhận các khu vực khó tiếp cận bằng đường bộ.", 40),
                        requirement("Chụp ảnh các vị trí được chỉ định",
                                "Ghi nhận chi tiết các vị trí khách hàng yêu cầu.", 50),
                        requirement("Ghi nhận hiện trạng sau khi hoàn tất giám sát",
                                "Lưu ảnh/video tổng kết khu vực tại thời điểm thực hiện mission.", 60)
                )
        );
    }

    private SuggestionSeed requirement(String label, String message, int sortOrder) {
        return new SuggestionSeed("Yêu cầu mặc định", label, message, sortOrder);
    }

    private void deactivateLegacySeedSuggestions() {
        suggestionRepository.findAll().stream()
                .filter(row -> REQUIREMENT_SOURCE.equalsIgnoreCase(row.getSource()))
                .forEach(row -> {
                    row.setActive(false);
                    suggestionRepository.save(row);
                });
    }

    private boolean upsertSuggestion(Service service, SuggestionSeed seed) {
        ServiceRequirementSuggestion suggestion = suggestionRepository
                .findByServiceIdAndCategoryIgnoreCaseAndLabelIgnoreCase(
                        service.getId(), seed.category(), seed.label())
                .orElseGet(ServiceRequirementSuggestion::new);

        boolean changed = false;
        if (suggestion.getService() == null || !service.getId().equals(suggestion.getService().getId())) {
            suggestion.setService(service);
            changed = true;
        }
        if (!seed.category().equals(suggestion.getCategory())) {
            suggestion.setCategory(seed.category());
            changed = true;
        }
        if (!seed.label().equals(suggestion.getLabel())) {
            suggestion.setLabel(seed.label());
            changed = true;
        }
        if (!seed.message().equals(suggestion.getMessage())) {
            suggestion.setMessage(seed.message());
            changed = true;
        }
        if (!Integer.valueOf(seed.sortOrder()).equals(suggestion.getSortOrder())) {
            suggestion.setSortOrder(seed.sortOrder());
            changed = true;
        }
        if (!Boolean.TRUE.equals(suggestion.getActive())) {
            suggestion.setActive(true);
            changed = true;
        }
        if (!REQUIREMENT_SOURCE.equals(suggestion.getSource())) {
            suggestion.setSource(REQUIREMENT_SOURCE);
            changed = true;
        }

        if (suggestion.getId() == null || changed) {
            suggestionRepository.save(suggestion);
            return true;
        }
        return false;
    }

    private record ServiceSeed(String code, String name, String description, long basePrice) {
    }

    private record DeliverableTypeSeed(String name, String defaultFormat) {
    }

    private record SuggestionSeed(String category, String label, String message, int sortOrder) {
    }
}
