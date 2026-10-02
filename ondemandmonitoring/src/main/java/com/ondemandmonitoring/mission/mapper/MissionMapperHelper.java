package com.ondemandmonitoring.mission.mapper;

import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.domain.MissionDeviceAssignment;
import com.ondemandmonitoring.mission.domain.MissionStaffAssignment;
import com.ondemandmonitoring.mission.dto.response.MissionPlanResponse;
import com.ondemandmonitoring.mission.dto.response.MissionStaffAssignmentResponse;
import com.ondemandmonitoring.mission.enums.MediaType;
import com.ondemandmonitoring.mission.enums.MissionStaffRole;
import com.ondemandmonitoring.mission.repository.MissionDeviceAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import com.ondemandmonitoring.mission.repository.MissionStaffAssignmentRepository;
import com.ondemandmonitoring.order.domain.OrderDeliverable;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MissionMapperHelper {

    private final MissionPlanRepository missionPlanRepository;
    private final MissionDeviceAssignmentRepository missionDeviceAssignmentRepository;
    private final MissionStaffAssignmentRepository missionStaffAssignmentRepository;
    private final MissionPlanMapper missionPlanMapper;
    private final MissionStaffAssignmentMapper missionStaffAssignmentMapper;

    @Named("missionLatitude")
    public Double getLatitude(Mission mission) {
        if (mission.getOrder() != null && mission.getOrder().getPoint() != null) {
            return mission.getOrder().getPoint().getY();
        }
        return null;
    }

    @Named("missionLongitude")
    public Double getLongitude(Mission mission) {
        if (mission.getOrder() != null && mission.getOrder().getPoint() != null) {
            return mission.getOrder().getPoint().getX();
        }
        return null;
    }

    @Named("missionRadiusM")
    public Double getRadiusM(Mission mission) {
        if (mission == null || mission.getOrder() == null || mission.getOrder().getDeliverables() == null) {
            return null;
        }

        return mission.getOrder().getDeliverables().stream()
                .filter(deliverable -> deliverable != null && deliverable.getRequirement() != null)
                .map(deliverable -> {
                    Double radius = toDouble(deliverable.getRequirement().get("radiusM"));
                    return radius != null ? radius : toDouble(deliverable.getRequirement().get("radius_m"));
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Named("missionMediaType")
    public MediaType getMediaType(Mission mission) {
        if (mission == null || mission.getOrder() == null || mission.getOrder().getDeliverables() == null) {
            return null;
        }
        String text = mission.getOrder().getDeliverables().stream()
                .filter(Objects::nonNull)
                .map(this::deliverableText)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .reduce("", (left, right) -> left + " " + right);
        if (text.contains("stream") || text.contains("livestream") || text.contains("live stream")) {
            return MediaType.STREAMING;
        }
        if (text.contains("video") || text.contains("mp4") || text.contains("mov")) {
            return MediaType.VIDEO;
        }
        if (text.contains("photo") || text.contains("image") || text.contains("ảnh")
                || text.contains("hình") || text.contains("jpg") || text.contains("jpeg")
                || text.contains("png")) {
            return MediaType.IMAGE;
        }
        return null;
    }

    @Named("missionMediaSummary")
    public String getMediaSummary(Mission mission) {
        if (mission == null || mission.getOrder() == null || mission.getOrder().getDeliverables() == null) {
            return null;
        }
        String summary = mission.getOrder().getDeliverables().stream()
                .filter(Objects::nonNull)
                .map(this::deliverableLabel)
                .filter(value -> !value.isBlank())
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
        return summary == null || summary.isBlank() ? null : summary;
    }

    @Named("missionDeviceId")
    public String getDeviceId(Mission mission) {
        MissionDeviceAssignment assignment = getCurrentDeviceAssignment(mission).orElse(null);
        if (assignment == null || assignment.getDevice() == null) {
            return null;
        }
        return assignment.getDevice().getId();
    }

    @Named("missionDeviceCode")
    public String getDeviceCode(Mission mission) {
        MissionDeviceAssignment assignment = getCurrentDeviceAssignment(mission).orElse(null);
        if (assignment == null || assignment.getDevice() == null) {
            return null;
        }
        return assignment.getDevice().getDeviceCode();
    }

    @Named("missionDeviceName")
    public String getDeviceName(Mission mission) {
        MissionDeviceAssignment assignment = getCurrentDeviceAssignment(mission).orElse(null);
        if (assignment == null || assignment.getDevice() == null) {
            return null;
        }
        return assignment.getDevice().getName();
    }

    @Named("missionStaffId")
    public String getStaffId(Mission mission) {
        MissionStaffAssignment assignment = getCurrentStaffAssignment(mission, MissionStaffRole.OPERATOR)
                .or(() -> getCurrentStaffAssignment(mission, MissionStaffRole.PILOT))
                .orElse(null);
        if (assignment == null || assignment.getStaff() == null) {
            return null;
        }
        return assignment.getStaff().getId();
    }

    @Named("missionStaffAssignments")
    public List<MissionStaffAssignmentResponse> getStaffAssignments(Mission mission) {
        if (mission == null || mission.getId() == null) {
            return List.of();
        }
        return missionStaffAssignmentRepository.findAllByMissionIdAndIsCurrentTrue(mission.getId()).stream()
                .sorted(Comparator.comparing(MissionStaffAssignment::getAssignedRole,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(missionStaffAssignmentMapper::toResponse)
                .toList();
    }

    @Named("missionPlan")
    public MissionPlanResponse getPlan(Mission mission) {
        if (mission == null || mission.getId() == null) {
            return null;
        }

        return missionPlanRepository.findByMissionId(mission.getId())
                .map(missionPlanMapper::toPlanResponse)
                .orElse(null);
    }

    private Optional<MissionDeviceAssignment> getCurrentDeviceAssignment(Mission mission) {
        if (mission == null || mission.getId() == null) {
            return Optional.empty();
        }
        List<MissionDeviceAssignment> current =
                missionDeviceAssignmentRepository.findAllByMissionIdAndIsCurrentTrueOrderByCreatedAtDesc(mission.getId());
        return !current.isEmpty()
                ? Optional.of(current.get(0))
                : missionDeviceAssignmentRepository.findFirstByMissionIdOrderByCreatedAtDesc(mission.getId());
    }

    private Optional<MissionStaffAssignment> getCurrentStaffAssignment(Mission mission, MissionStaffRole role) {
        if (mission == null || mission.getId() == null) {
            return Optional.empty();
        }
        return missionStaffAssignmentRepository.findAllByMissionIdAndAssignedRoleAndIsCurrentTrue(mission.getId(), role)
                .stream()
                .findFirst();
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String deliverableLabel(OrderDeliverable deliverable) {
        if (deliverable.getDeliverableType() == null) {
            return "";
        }
        String name = deliverable.getDeliverableType().getName();
        String format = deliverable.getDeliverableType().getDefaultFormat();
        if (format == null || format.isBlank()) {
            return name == null ? "" : name;
        }
        return (name == null || name.isBlank()) ? format : name + " (" + format + ")";
    }

    private String deliverableText(OrderDeliverable deliverable) {
        if (deliverable.getDeliverableType() == null) {
            return "";
        }
        return (deliverable.getDeliverableType().getName() == null ? "" : deliverable.getDeliverableType().getName())
                + " "
                + (deliverable.getDeliverableType().getDefaultFormat() == null ? "" : deliverable.getDeliverableType().getDefaultFormat());
    }
}
