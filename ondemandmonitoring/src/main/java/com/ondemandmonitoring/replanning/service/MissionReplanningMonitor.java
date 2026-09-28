package com.ondemandmonitoring.replanning.service;

import com.ondemandmonitoring.device.domain.DeviceTelemetry;
import com.ondemandmonitoring.device.repository.DeviceTelemetryRepository;
import com.ondemandmonitoring.mission.domain.MissionDeviceAssignment;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionDeviceAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import com.ondemandmonitoring.replanning.config.ReplanningProperties;
import com.ondemandmonitoring.replanning.dto.ReplanningDecision;
import com.ondemandmonitoring.replanning.event.DeviceTelemetrySavedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class MissionReplanningMonitor {

    private static final List<MissionStatus> ACTIVE_STATUSES = List.of(
            MissionStatus.IN_FLIGHT,
            MissionStatus.IN_PROGRESS);

    private final DeviceTelemetryRepository deviceTelemetryRepository;
    private final MissionDeviceAssignmentRepository missionDeviceAssignmentRepository;
    private final MissionPlanRepository missionPlanRepository;
    private final ReplanningPolicy replanningPolicy;
    private final MissionReplanningService missionReplanningService;
    private final ReplanningProperties properties;
    private final Map<String, Instant> lastReplannedAt = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> runningByMission = new ConcurrentHashMap<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTelemetrySaved(DeviceTelemetrySavedEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            evaluate(event);
        } catch (Exception exception) {
            log.warn(
                    "Dynamic replanning monitor failed for deviceId={} telemetryId={}: {}",
                    event.deviceId(),
                    event.telemetryId(),
                    exception.getMessage(),
                    exception);
        }
    }

    private void evaluate(DeviceTelemetrySavedEvent event) {
        DeviceTelemetry telemetry = deviceTelemetryRepository.findById(event.telemetryId()).orElse(null);
        if (telemetry == null) {
            return;
        }

        List<MissionDeviceAssignment> assignments = missionDeviceAssignmentRepository
                .findCurrentByDeviceCodeAndMissionStatusIn(
                        event.deviceId(),
                        ACTIVE_STATUSES);
        if (assignments.isEmpty()) {
            return;
        }

        for (MissionDeviceAssignment assignment : assignments) {
            String missionId = assignment.getMission().getId();
            Optional<MissionPlan> currentPlan = missionPlanRepository.findByMissionId(missionId);
            if (currentPlan.isEmpty()) {
                log.debug("Skipping dynamic replan for missionId={} because no active plan exists.", missionId);
                continue;
            }
            if (!canRun(missionId, currentPlan.get())) {
                continue;
            }

            ReplanningDecision decision = replanningPolicy.evaluate(telemetry, currentPlan.get());
            if (!decision.required()) {
                log.debug("No dynamic replan for missionId={}: {}", missionId, decision.detail());
                continue;
            }

            runReplan(missionId, telemetry, decision);
        }
    }

    private boolean canRun(String missionId, MissionPlan currentPlan) {
        int replanCount = Math.max(0, Optional.ofNullable(currentPlan.getPlanVersion()).orElse(1) - 1);
        if (replanCount >= properties.getMaxReplansPerMission()) {
            log.info("Dynamic replan blocked for missionId={} because max replans reached.", missionId);
            return false;
        }

        Instant lastRun = lastReplannedAt.get(missionId);
        if (lastRun != null
                && Duration.between(lastRun, Instant.now()).getSeconds() < properties.getCooldownSeconds()) {
            return false;
        }

        AtomicBoolean running = runningByMission.computeIfAbsent(missionId, ignored -> new AtomicBoolean(false));
        return running.compareAndSet(false, true);
    }

    private void runReplan(String missionId, DeviceTelemetry telemetry, ReplanningDecision decision) {
        try {
            Instant startedAt = Instant.now();
            MissionPlan plan = missionReplanningService.replanFromTelemetry(missionId, telemetry, decision.reason());
            lastReplannedAt.put(missionId, Instant.now());
            log.info(
                    "Dynamic replan evaluated missionId={} deviceId={} reason={} detail={} planVersion={} status={} battery={} simX={} simY={} durationMs={}",
                    missionId,
                    resolveDeviceCode(telemetry),
                    decision.reason(),
                    decision.detail(),
                    plan.getPlanVersion(),
                    plan.getReplanningStatus(),
                    telemetry.getBatteryPercent(),
                    telemetry.getSimX(),
                    telemetry.getSimY(),
                    Duration.between(startedAt, Instant.now()).toMillis());
        } catch (Exception exception) {
            lastReplannedAt.put(missionId, Instant.now());
            log.warn(
                    "Dynamic replan failed missionId={} deviceId={} reason={} detail={}: {}",
                    missionId,
                    resolveDeviceCode(telemetry),
                    decision.reason(),
                    decision.detail(),
                    exception.getMessage(),
                    exception);
        } finally {
            AtomicBoolean running = runningByMission.get(missionId);
            if (running != null) {
                running.set(false);
            }
        }
    }

    private String resolveDeviceCode(DeviceTelemetry telemetry) {
        if (telemetry == null
                || telemetry.getDeviceConnection() == null
                || telemetry.getDeviceConnection().getDevice() == null) {
            return null;
        }
        return telemetry.getDeviceConnection().getDevice().getDeviceCode();
    }
}

