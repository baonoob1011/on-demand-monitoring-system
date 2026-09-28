package com.ondemandmonitoring.device.service.impl;

import com.ondemandmonitoring.device.service.IPersistedPreflightCheckService;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.device.domain.PersistedPreDeviceCheck;
import com.ondemandmonitoring.device.domain.PersistedPreDeviceCheckItem;
import com.ondemandmonitoring.device.dto.request.PreflightItemUpdateRequest;
import com.ondemandmonitoring.device.dto.response.PersistedPreDeviceCheckResponse;
import com.ondemandmonitoring.device.enums.PreflightCheckLevel;
import com.ondemandmonitoring.device.enums.PreflightCheckStatus;
import com.ondemandmonitoring.device.enums.PreflightItemStatus;
import com.ondemandmonitoring.device.repository.PersistedPreflightCheckRepository;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersistedPreflightCheckService implements IPersistedPreflightCheckService {

    private static final List<Definition> CHECKS = List.of(
            new Definition("GAZEBO", "Gazebo Simulation", PreflightCheckLevel.CRITICAL),
            new Definition("PX4", "PX4 Flight Controller", PreflightCheckLevel.CRITICAL),
            new Definition("MAVSDK", "MAVSDK Connection", PreflightCheckLevel.CRITICAL),
            new Definition("PX4_CONTROL", "PX4 Control", PreflightCheckLevel.CRITICAL),
            new Definition("LOCAL_POSITION", "Local Position", PreflightCheckLevel.CRITICAL),
            new Definition("MAVSDK_HEALTH", "MAVSDK Health", PreflightCheckLevel.CRITICAL),
            new Definition("BATTERY", "Battery", PreflightCheckLevel.CRITICAL),
            new Definition("LIDAR", "LiDAR", PreflightCheckLevel.WARNING),
            new Definition("CAMERA", "Downward Camera", PreflightCheckLevel.WARNING),
            new Definition("BACKEND", "Backend Connection", PreflightCheckLevel.WARNING),
            new Definition("MEDIA", "Media Upload", PreflightCheckLevel.INFO),
            new Definition("MODULES", "Module Check", PreflightCheckLevel.INFO));

    private final PersistedPreflightCheckRepository runRepository;
    private final MissionRepository missionRepository;

    @Transactional
    @Override
    public PersistedPreDeviceCheckResponse start(String missionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.MISSION_NOT_FOUND,
                        "Mission not found: " + missionId));

        PersistedPreDeviceCheck run = new PersistedPreDeviceCheck();
        run.setMission(mission);
        run.setStatus(PreflightCheckStatus.CHECKING);
        run.setTotalChecks(CHECKS.size());
        run.setPassedChecks(0);
        run.setFailedChecks(0);
        run.setStartedAt(Instant.now());

        for (Definition definition : CHECKS) {
            PersistedPreDeviceCheckItem item = new PersistedPreDeviceCheckItem();
            item.setPreflightCheck(run);
            item.setCheckType(definition.type);
            item.setCheckName(definition.name);
            item.setCheckLevel(definition.level);
            run.getItems().add(item);
        }

        return PersistedPreDeviceCheckResponse.from(runRepository.save(run));
    }

    @Transactional(readOnly = true)
    @Override
    public List<PersistedPreDeviceCheckResponse> history(String missionId) {
        ensureMission(missionId);

        return runRepository.findByMissionIdOrderByCreatedAtDesc(missionId)
                .stream()
                .map(PersistedPreDeviceCheckResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public PersistedPreDeviceCheckResponse current(String missionId) {
        ensureMission(missionId);

        return runRepository.findFirstByMissionIdOrderByCreatedAtDesc(missionId)
                .map(PersistedPreDeviceCheckResponse::from)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "No preflight check found"));
    }

    @Transactional(readOnly = true)
    @Override
    public PersistedPreDeviceCheckResponse get(String id) {
        return PersistedPreDeviceCheckResponse.from(runRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Preflight check not found: " + id)));
    }

    @Transactional
    @Override
    public PersistedPreDeviceCheckResponse update(String id, String type, PreflightItemUpdateRequest request) {
        PersistedPreDeviceCheck run = runRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Preflight check not found: " + id));

        PersistedPreDeviceCheckItem item = run.getItems().stream()
                .filter(candidate -> type.equals(candidate.getCheckType()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Unknown check type: " + type));

        item.setStatus(request.getStatus());
        item.setMessage(request.getMessage());
        item.setCheckedAt(Instant.now());

        int passed = (int) run.getItems().stream()
                .filter(i -> i.getStatus() == PreflightItemStatus.PASSED)
                .count();
        int failed = (int) run.getItems().stream()
                .filter(i -> i.getStatus() == PreflightItemStatus.FAILED)
                .count();
        boolean criticalFailed = run.getItems().stream()
                .anyMatch(i -> i.getCheckLevel() == PreflightCheckLevel.CRITICAL
                        && i.getStatus() == PreflightItemStatus.FAILED);
        boolean finished = run.getItems().stream()
                .allMatch(i -> i.getStatus() == PreflightItemStatus.PASSED
                        || i.getStatus() == PreflightItemStatus.FAILED);

        run.setPassedChecks(passed);
        run.setFailedChecks(failed);

        if (criticalFailed) {
            run.setStatus(PreflightCheckStatus.FAILED);
        } else if (finished) {
            run.setStatus(PreflightCheckStatus.PASSED);
        } else {
            run.setStatus(PreflightCheckStatus.CHECKING);
        }

        if (run.getStatus() != PreflightCheckStatus.CHECKING) {
            run.setCompletedAt(Instant.now());
        }

        return PersistedPreDeviceCheckResponse.from(runRepository.save(run));
    }

    private void ensureMission(String id) {
        if (!missionRepository.existsById(id)) {
            throw new ApiException(
                    ErrorCode.MISSION_NOT_FOUND,
                    "Mission not found: " + id);
        }
    }

    private record Definition(String type, String name, PreflightCheckLevel level) {}
}

