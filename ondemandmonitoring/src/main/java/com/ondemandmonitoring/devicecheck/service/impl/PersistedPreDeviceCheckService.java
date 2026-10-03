package com.ondemandmonitoring.devicecheck.service.impl;

import com.ondemandmonitoring.devicecheck.service.IPersistedPreDeviceCheckService;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.devicecheck.domain.PersistedPreDeviceCheck;
import com.ondemandmonitoring.devicecheck.domain.PersistedPreDeviceCheckItem;
import com.ondemandmonitoring.devicecheck.dto.request.PreDeviceCheckItemUpdateRequest;
import com.ondemandmonitoring.devicecheck.dto.response.PersistedPreDeviceCheckResponse;
import com.ondemandmonitoring.devicecheck.enums.PreDeviceCheckStatus;
import com.ondemandmonitoring.devicecheck.enums.PreDeviceCheckType;
import com.ondemandmonitoring.devicecheck.enums.PreDeviceItemStatus;
import com.ondemandmonitoring.devicecheck.repository.PersistedPreDeviceCheckRepository;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.repository.MissionRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersistedPreDeviceCheckService implements IPersistedPreDeviceCheckService {

    private static final long START_IDEMPOTENCY_WINDOW_SECONDS = 30;
    private static final List<PreDeviceCheckType> CHECKS = Arrays.asList(PreDeviceCheckType.values());

    private final PersistedPreDeviceCheckRepository runRepository;
    private final MissionRepository missionRepository;

    @Transactional
    @Override
    @PreAuthorize("@missionAuthorizationService.canInspectDevice(#missionId)")
    public PersistedPreDeviceCheckResponse start(String missionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.MISSION_NOT_FOUND,
                        "Mission not found: " + missionId));
        if (mission.getStatus() != com.ondemandmonitoring.mission.enums.MissionStatus.CONNECTED
                && mission.getStatus() != com.ondemandmonitoring.mission.enums.MissionStatus.PREFLIGHT_CHECKING) {
            throw new ApiException(ErrorCode.MISSION_STATUS_INVALID, "Pre-device inspection requires a connected mission");
        }

        PersistedPreDeviceCheck activeRun = runRepository
                .findFirstByMissionIdOrderByCreatedAtDesc(missionId)
                .filter(run -> run.getStatus() == PreDeviceCheckStatus.CHECKING
                        && run.getStartedAt() != null
                        && run.getStartedAt().isAfter(
                                Instant.now().minusSeconds(START_IDEMPOTENCY_WINDOW_SECONDS)))
                .orElse(null);
        if (activeRun != null) {
            return PersistedPreDeviceCheckResponse.from(activeRun);
        }

        PersistedPreDeviceCheck run = new PersistedPreDeviceCheck();
        run.setMission(mission);
        run.setStatus(PreDeviceCheckStatus.CHECKING);
        run.setTotalChecks(CHECKS.size());
        run.setPassedChecks(0);
        run.setFailedChecks(0);
        run.setStartedAt(Instant.now());

        for (PreDeviceCheckType definition : CHECKS) {
            PersistedPreDeviceCheckItem item = new PersistedPreDeviceCheckItem();
            item.setPreDeviceCheck(run);
            item.setCheckType(definition.code());
            item.setCheckName(definition.displayName());
            item.setCheckLevel(definition.level());
            run.getItems().add(item);
        }

        return PersistedPreDeviceCheckResponse.from(runRepository.save(run));
    }

    @Transactional(readOnly = true)
    @Override
    @PreAuthorize("@missionAuthorizationService.canViewMission(#missionId)")
    public List<PersistedPreDeviceCheckResponse> history(String missionId) {
        ensureMission(missionId);

        return runRepository.findByMissionIdOrderByCreatedAtDesc(missionId)
                .stream()
                .map(PersistedPreDeviceCheckResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    @PreAuthorize("@missionAuthorizationService.canViewMission(#missionId)")
    public PersistedPreDeviceCheckResponse current(String missionId) {
        ensureMission(missionId);

        return runRepository.findFirstByMissionIdOrderByCreatedAtDesc(missionId)
                .map(PersistedPreDeviceCheckResponse::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    @Override
    @PreAuthorize("@deviceCheckAuthorizationService.canViewPreCheck(#id)")
    public PersistedPreDeviceCheckResponse get(String id) {
        return PersistedPreDeviceCheckResponse.from(runRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Preflight check not found: " + id)));
    }

    @Transactional
    @Override
    @PreAuthorize("@deviceCheckAuthorizationService.canInspectPreCheck(#id)")
    public PersistedPreDeviceCheckResponse update(String id, String type, PreDeviceCheckItemUpdateRequest request) {
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

        if (isTerminal(item.getStatus()) && !isTerminal(request.getStatus())) {
            return PersistedPreDeviceCheckResponse.from(run);
        }

        item.setStatus(request.getStatus());
        item.setMessage(request.getMessage());
        item.setCheckedAt(Instant.now());

        int passed = (int) run.getItems().stream()
                .filter(i -> i.getStatus() == PreDeviceItemStatus.PASSED)
                .count();
        int failed = (int) run.getItems().stream()
                .filter(i -> i.getStatus() == PreDeviceItemStatus.FAILED)
                .count();
        boolean failedItem = run.getItems().stream()
                .anyMatch(i -> i.getStatus() == PreDeviceItemStatus.FAILED);
        boolean finished = run.getItems().stream()
                .allMatch(i -> i.getStatus() == PreDeviceItemStatus.PASSED
                        || i.getStatus() == PreDeviceItemStatus.FAILED);

        run.setPassedChecks(passed);
        run.setFailedChecks(failed);

        if (failedItem) {
            run.setStatus(PreDeviceCheckStatus.FAILED);
        } else if (finished) {
            run.setStatus(PreDeviceCheckStatus.PASSED);
        } else {
            run.setStatus(PreDeviceCheckStatus.CHECKING);
        }

        if (run.getStatus() != PreDeviceCheckStatus.CHECKING) {
            run.setCompletedAt(Instant.now());
        }

        return PersistedPreDeviceCheckResponse.from(run);
    }

    private boolean isTerminal(PreDeviceItemStatus status) {
        return status == PreDeviceItemStatus.PASSED || status == PreDeviceItemStatus.FAILED;
    }

    private void ensureMission(String id) {
        if (!missionRepository.existsById(id)) {
            throw new ApiException(
                    ErrorCode.MISSION_NOT_FOUND,
                    "Mission not found: " + id);
        }
    }
}
