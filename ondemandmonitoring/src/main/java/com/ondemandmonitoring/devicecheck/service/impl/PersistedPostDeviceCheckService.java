package com.ondemandmonitoring.devicecheck.service.impl;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.devicecheck.domain.PersistedPostDeviceCheck;
import com.ondemandmonitoring.devicecheck.domain.PersistedPostDeviceCheckItem;
import com.ondemandmonitoring.devicecheck.dto.request.DeviceCheckItemUpdateRequest;
import com.ondemandmonitoring.devicecheck.dto.response.PersistedPostDeviceCheckResponse;
import com.ondemandmonitoring.devicecheck.enums.DeviceCheckItemStatus;
import com.ondemandmonitoring.devicecheck.enums.DeviceCheckLevel;
import com.ondemandmonitoring.devicecheck.enums.DeviceCheckStatus;
import com.ondemandmonitoring.devicecheck.repository.PersistedPostDeviceCheckRepository;
import com.ondemandmonitoring.devicecheck.service.IPersistedPostDeviceCheckService;
import com.ondemandmonitoring.mission.domain.DeviceConnection;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.repository.DeviceConnectionRepository;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersistedPostDeviceCheckService implements IPersistedPostDeviceCheckService {

    private static final List<Definition> CHECKS = List.of(
            new Definition("AIRFRAME", "Airframe Condition", DeviceCheckLevel.CRITICAL),
            new Definition("PROPELLERS", "Propellers And Motors", DeviceCheckLevel.CRITICAL),
            new Definition("BATTERY", "Landing Battery", DeviceCheckLevel.CRITICAL),
            new Definition("CAMERA", "Camera And Payload", DeviceCheckLevel.WARNING),
            new Definition("COMMUNICATION", "Communication Logs", DeviceCheckLevel.WARNING),
            new Definition("MEDIA", "Media Upload", DeviceCheckLevel.INFO),
            new Definition("TELEMETRY", "Telemetry Summary", DeviceCheckLevel.INFO));

    private final PersistedPostDeviceCheckRepository runRepository;
    private final MissionRepository missionRepository;
    private final DeviceConnectionRepository deviceConnectionRepository;

    @Transactional
    @Override
    public PersistedPostDeviceCheckResponse start(String missionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.MISSION_NOT_FOUND,
                        "Mission not found: " + missionId));
        DeviceConnection deviceConnection = deviceConnectionRepository
                .findTopByMissionIdAndConnectionStatusOrderByConnectedAtDesc(missionId, "CONNECTED")
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "No active device connection found for mission: " + missionId));

        PersistedPostDeviceCheck run = new PersistedPostDeviceCheck();
        run.setMission(mission);
        run.setDeviceConnection(deviceConnection);
        run.setStatus(DeviceCheckStatus.CHECKING);
        run.setTotalChecks(CHECKS.size());
        run.setPassedChecks(0);
        run.setFailedChecks(0);
        run.setStartedAt(Instant.now());

        for (Definition definition : CHECKS) {
            PersistedPostDeviceCheckItem item = new PersistedPostDeviceCheckItem();
            item.setPostDeviceCheck(run);
            item.setCheckType(definition.type);
            item.setCheckName(definition.name);
            item.setCheckLevel(definition.level);
            run.getItems().add(item);
        }

        return PersistedPostDeviceCheckResponse.from(runRepository.save(run));
    }

    @Transactional(readOnly = true)
    @Override
    public List<PersistedPostDeviceCheckResponse> history(String missionId) {
        ensureMission(missionId);
        return runRepository.findByMissionIdOrderByCreatedAtDesc(missionId)
                .stream()
                .map(PersistedPostDeviceCheckResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public PersistedPostDeviceCheckResponse current(String missionId) {
        ensureMission(missionId);
        return runRepository.findFirstByMissionIdOrderByCreatedAtDesc(missionId)
                .map(PersistedPostDeviceCheckResponse::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    @Override
    public PersistedPostDeviceCheckResponse get(String id) {
        return PersistedPostDeviceCheckResponse.from(runRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Post-device check not found: " + id)));
    }

    @Transactional
    @Override
    public PersistedPostDeviceCheckResponse update(String id, String type, DeviceCheckItemUpdateRequest request) {
        PersistedPostDeviceCheck run = runRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Post-device check not found: " + id));

        PersistedPostDeviceCheckItem item = run.getItems().stream()
                .filter(candidate -> type.equals(candidate.getCheckType()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Unknown check type: " + type));

        item.setStatus(request.getStatus());
        item.setMessage(request.getMessage());
        item.setCheckedAt(Instant.now());

        int passed = (int) run.getItems().stream()
                .filter(i -> i.getStatus() == DeviceCheckItemStatus.PASSED)
                .count();
        int failed = (int) run.getItems().stream()
                .filter(i -> i.getStatus() == DeviceCheckItemStatus.FAILED)
                .count();
        boolean criticalFailed = run.getItems().stream()
                .anyMatch(i -> i.getCheckLevel() == DeviceCheckLevel.CRITICAL
                        && i.getStatus() == DeviceCheckItemStatus.FAILED);
        boolean finished = run.getItems().stream()
                .allMatch(i -> i.getStatus() == DeviceCheckItemStatus.PASSED
                        || i.getStatus() == DeviceCheckItemStatus.FAILED);

        run.setPassedChecks(passed);
        run.setFailedChecks(failed);
        if (criticalFailed) {
            run.setStatus(DeviceCheckStatus.FAILED);
        } else if (finished) {
            run.setStatus(DeviceCheckStatus.PASSED);
        } else {
            run.setStatus(DeviceCheckStatus.CHECKING);
        }
        if (run.getStatus() != DeviceCheckStatus.CHECKING) {
            run.setCompletedAt(Instant.now());
        }

        return PersistedPostDeviceCheckResponse.from(runRepository.save(run));
    }

    private void ensureMission(String id) {
        if (!missionRepository.existsById(id)) {
            throw new ApiException(
                    ErrorCode.MISSION_NOT_FOUND,
                    "Mission not found: " + id);
        }
    }

    private record Definition(String type, String name, DeviceCheckLevel level) {}
}


