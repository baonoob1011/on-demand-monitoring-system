package com.ondemandmonitoring.Consultation.config;

import com.ondemandmonitoring.drone.domain.DronePayload;
import com.ondemandmonitoring.drone.repository.DronePayloadRepository;
import com.ondemandmonitoring.service.domain.DeliverableType;
import com.ondemandmonitoring.service.domain.Service;
import com.ondemandmonitoring.service.domain.ServiceDeliverable;
import com.ondemandmonitoring.service.repository.DeliverableTypeRepository;
import com.ondemandmonitoring.service.repository.ServiceDeliverableRepository;
import com.ondemandmonitoring.service.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(30)
@RequiredArgsConstructor
@Slf4j
public class RagKnowledgeSeedDataInitializer implements ApplicationRunner {

    private final ServiceRepository serviceRepository;
    private final DeliverableTypeRepository deliverableTypeRepository;
    private final ServiceDeliverableRepository serviceDeliverableRepository;
    private final DronePayloadRepository dronePayloadRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Bắt đầu khởi tạo dữ liệu tri thức RAG...");

        int servicesCreated = seedServices();
        int deliverableTypesCreated = seedDeliverableTypes();
        int serviceDeliverablesCreated = seedServiceDeliverables();
        int dronePayloadsCreated = seedDronePayloads();

        log.info("Hoàn tất khởi tạo dữ liệu tri thức RAG:");
        log.info("Dịch vụ: {}", servicesCreated);
        log.info("Loại kết quả bàn giao: {}", deliverableTypesCreated);
        log.info("Kết quả bàn giao theo dịch vụ: {}", serviceDeliverablesCreated);
        log.info("Thiết bị tải trọng drone: {}", dronePayloadsCreated);
    }

    private int seedServices() {
        int count = 0;

        List<Service> services = List.of(
                Service.builder()
                        .name("Giám sát Tòa nhà / Cơ sở hạ tầng")
                        .description(
                                "Giám sát và kiểm tra toàn diện tòa nhà, cầu và các công trình cơ sở hạ tầng. " +
                                        "Hỗ trợ kiểm tra tình trạng kết cấu, phát hiện vết nứt, hư hỏng và kiểm tra bề mặt công trình."
                        )
                        .isActive(true)
                        .build(),

                Service.builder()
                        .name("Giám sát Nông nghiệp / Cây trồng")
                        .description(
                                "Giám sát khu vực nông nghiệp nhằm đánh giá tình trạng và sức khỏe cây trồng, " +
                                        "phát hiện khu vực phát triển bất thường, dấu hiệu bệnh và đánh giá hiệu quả tưới tiêu."
                        )
                        .isActive(true)
                        .build(),

                Service.builder()
                        .name("Giám sát Môi trường")
                        .description(
                                "Giám sát các điều kiện môi trường, bao gồm phát hiện bất thường nhiệt độ, " +
                                        "theo dõi ô nhiễm và đánh giá ảnh hưởng của các nguy cơ tự nhiên như ngập lụt hoặc sạt lở."
                        )
                        .isActive(true)
                        .build(),

                Service.builder()
                        .name("Giám sát Tiến độ Xây dựng")
                        .description(
                                "Kiểm tra công trường định kỳ nhằm ghi nhận và theo dõi tiến độ xây dựng, " +
                                        "đối chiếu tình trạng thực tế với kế hoạch và phát hiện các nguy cơ an toàn tại công trường."
                        )
                        .isActive(true)
                        .build(),

                Service.builder()
                        .name("Kiểm tra Tấm pin Năng lượng Mặt trời")
                        .description(
                                "Kiểm tra nhiệt và hình ảnh trực quan của hệ thống pin năng lượng mặt trời " +
                                        "nhằm phát hiện tấm pin bị lỗi, điểm nóng và các hư hỏng vật lý có thể ảnh hưởng đến hiệu suất phát điện."
                        )
                        .isActive(true)
                        .build()
        );

        for (Service service : services) {
            if (!serviceRepository.existsByNameIgnoreCase(service.getName())) {
                serviceRepository.save(service);
                count++;
            }
        }

        return count;
    }

    private int seedDeliverableTypes() {
        int count = 0;

        List<DeliverableType> types = List.of(
                DeliverableType.builder()
                        .name("Báo cáo Giám sát")
                        .defaultFormat("PDF")
                        .isActive(true)
                        .build(),

                DeliverableType.builder()
                        .name("Hình ảnh Kiểm tra")
                        .defaultFormat("JPG")
                        .isActive(true)
                        .build(),

                DeliverableType.builder()
                        .name("Báo cáo Phân tích Nhiệt")
                        .defaultFormat("PDF")
                        .isActive(true)
                        .build(),

                DeliverableType.builder()
                        .name("Báo cáo Bất thường")
                        .defaultFormat("PDF")
                        .isActive(true)
                        .build(),

                DeliverableType.builder()
                        .name("Báo cáo Tiến độ")
                        .defaultFormat("PDF")
                        .isActive(true)
                        .build(),

                DeliverableType.builder()
                        .name("Bản đồ Khu vực")
                        .defaultFormat("GeoJSON")
                        .isActive(true)
                        .build(),

                DeliverableType.builder()
                        .name("Video Ghi hình")
                        .defaultFormat("MP4")
                        .isActive(true)
                        .build()
        );

        for (DeliverableType type : types) {
            if (!deliverableTypeRepository.existsByNameIgnoreCase(type.getName())) {
                deliverableTypeRepository.save(type);
                count++;
            }
        }

        return count;
    }

    private int seedServiceDeliverables() {
        int count = 0;

        Map<String, List<String>> mapping = new HashMap<>();

        mapping.put(
                "Giám sát Tòa nhà / Cơ sở hạ tầng",
                List.of(
                        "Báo cáo Giám sát",
                        "Hình ảnh Kiểm tra",
                        "Báo cáo Phân tích Nhiệt",
                        "Báo cáo Bất thường",
                        "Video Ghi hình"
                )
        );

        mapping.put(
                "Giám sát Nông nghiệp / Cây trồng",
                List.of(
                        "Báo cáo Giám sát",
                        "Hình ảnh Kiểm tra",
                        "Bản đồ Khu vực",
                        "Báo cáo Bất thường"
                )
        );

        mapping.put(
                "Giám sát Môi trường",
                List.of(
                        "Báo cáo Giám sát",
                        "Bản đồ Khu vực",
                        "Báo cáo Phân tích Nhiệt",
                        "Video Ghi hình"
                )
        );

        mapping.put(
                "Giám sát Tiến độ Xây dựng",
                List.of(
                        "Báo cáo Tiến độ",
                        "Hình ảnh Kiểm tra",
                        "Video Ghi hình",
                        "Bản đồ Khu vực"
                )
        );

        mapping.put(
                "Kiểm tra Tấm pin Năng lượng Mặt trời",
                List.of(
                        "Báo cáo Phân tích Nhiệt",
                        "Hình ảnh Kiểm tra",
                        "Báo cáo Bất thường",
                        "Báo cáo Giám sát"
                )
        );

        List<Service> allServices = serviceRepository.findAll();
        List<DeliverableType> allTypes = deliverableTypeRepository.findAll();

        for (Service service : allServices) {
            List<String> expectedDeliverables = mapping.get(service.getName());

            if (expectedDeliverables == null) {
                continue;
            }

            for (String deliverableName : expectedDeliverables) {
                DeliverableType type = allTypes.stream()
                        .filter(t -> t.getName().equalsIgnoreCase(deliverableName))
                        .findFirst()
                        .orElse(null);

                if (type == null) {
                    continue;
                }

                if (!serviceDeliverableRepository
                        .existsByServiceIdAndDeliverableTypeId(
                                service.getId(),
                                type.getId()
                        )) {

                    ServiceDeliverable serviceDeliverable =
                            ServiceDeliverable.builder()
                                    .service(service)
                                    .deliverableType(type)
                                    .build();

                    serviceDeliverableRepository.save(serviceDeliverable);
                    count++;
                }
            }
        }

        return count;
    }

    private int seedDronePayloads() {
        int count = 0;

        List<DronePayload> payloads = List.of(
                new DronePayload(
                        "Zenmuse H20T",
                        "MULTISENSOR",
                        0.828,
                        "Tích hợp camera RGB và camera nhiệt. Phù hợp cho kiểm tra công trình, " +
                                "phát hiện điểm nóng, quan sát từ xa và các nhiệm vụ giám sát cần kết hợp hình ảnh thường với hình ảnh nhiệt."
                ),

                new DronePayload(
                        "Zenmuse P1",
                        "RGB_MAPPING",
                        0.800,
                        "Camera full-frame độ phân giải cao phục vụ đo ảnh và lập bản đồ. " +
                                "Phù hợp cho tạo bản đồ 2D/3D chi tiết và theo dõi tiến độ xây dựng."
                ),

                new DronePayload(
                        "MicaSense RedEdge-MX",
                        "MULTISPECTRAL",
                        0.232,
                        "Cảm biến đa phổ phục vụ giám sát nông nghiệp chính xác, đánh giá sức khỏe cây trồng, " +
                                "phát hiện khu vực cây trồng bất thường và xây dựng bản đồ chỉ số thực vật như NDVI."
                ),

                new DronePayload(
                        "Zenmuse L1",
                        "LIDAR",
                        0.930,
                        "Tích hợp LiDAR và camera RGB để tạo dữ liệu đám mây điểm có độ chính xác cao. " +
                                "Phù hợp cho kiểm tra cơ sở hạ tầng, khảo sát địa hình và thu thập dữ liệu tại khu vực có thảm thực vật dày."
                ),

                new DronePayload(
                        "Zenmuse Z30",
                        "RGB_ZOOM",
                        0.549,
                        "Camera zoom quang học 30x cho phép kiểm tra chi tiết từ khoảng cách an toàn. " +
                                "Phù hợp cho kiểm tra cơ sở hạ tầng, vị trí khó tiếp cận và môi trường có nguy cơ cao."
                )
        );

        for (DronePayload payload : payloads) {
            if (!dronePayloadRepository
                    .existsByModelNameIgnoreCase(payload.getModelName())) {

                dronePayloadRepository.save(payload);
                count++;
            }
        }

        return count;
    }
}