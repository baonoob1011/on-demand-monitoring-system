package com.ondemandmonitoring.replanning.service;

import com.ondemandmonitoring.drone.domain.DroneTelemetry;
import com.ondemandmonitoring.drone.repository.DroneTelemetryRepository;
import com.ondemandmonitoring.mission.domain.MissionDroneAssignment;
import com.ondemandmonitoring.mission.domain.MissionPlan;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import com.ondemandmonitoring.mission.repository.MissionDroneAssignmentRepository;
import com.ondemandmonitoring.mission.repository.MissionPlanRepository;
import com.ondemandmonitoring.replanning.config.ReplanningProperties;
import com.ondemandmonitoring.replanning.dto.ReplanningDecision;
import com.ondemandmonitoring.replanning.event.DroneTelemetrySavedEvent;
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

    private final DroneTelemetryRepository droneTelemetryRepository;
    private final MissionDroneAssignmentRepository missionDroneAssignmentRepository;
    private final MissionPlanRepository missionPlanRepository;
    private final ReplanningPolicy replanningPolicy;
    private final MissionReplanningService missionReplanningService;
    private final ReplanningProperties properties;
    private final Map<String, Instant> lastReplannedAt = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> runningByMission = new ConcurrentHashMap<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTelemetrySaved(DroneTelemetrySavedEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            evaluate(event);
        } catch (Exception exception) {
            log.warn(
                    "Dynamic replanning monitor failed for droneCode={} telemetryId={}: {}",
                    event.droneCode(),
                    event.telemetryId(),
                    exception.getMessage(),
                    exception);
        }
    }

    private void evaluate(DroneTelemetrySavedEvent event) {
        DroneTelemetry telemetry = droneTelemetryRepository.findById(event.telemetryId()).orElse(null);
        if (telemetry == null) {
            return;
        }

        List<MissionDroneAssignment> assignments =
                missionDroneAssignmentRepository.findCurrentByDroneCodeAndMissionStatusIn(
                        event.droneCode(),
                        ACTIVE_STATUSES);
        if (assignments.isEmpty()) {
            return;
        }

        for (MissionDroneAssignment assignment : assignments) {
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

    private void runReplan(String missionId, DroneTelemetry telemetry, ReplanningDecision decision) {
        try {
            Instant startedAt = Instant.now();
            MissionPlan plan = missionReplanningService.replanFromTelemetry(missionId, telemetry, decision.reason());
            lastReplannedAt.put(missionId, Instant.now());
            log.info(
                    "Dynamic replan evaluated missionId={} droneCode={} reason={} detail={} planVersion={} status={} battery={} simX={} simY={} durationMs={}",
                    missionId,
                    telemetry.getDroneCode(),
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
                    "Dynamic replan failed missionId={} droneCode={} reason={} detail={}: {}",
                    missionId,
                    telemetry.getDroneCode(),
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
}
