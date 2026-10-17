package com.ondemandmonitoring.media.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "odms.result-retention.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExpiredResultCleanupJob {
    private final ResultRetentionCleanupService cleanupService;

    @Scheduled(cron = "${odms.result-retention.cleanup.cron:0 15 * * * *}")
    public void run() {
        log.info("Starting expired result cleanup");
        var summary = cleanupService.cleanupExpiredResults();
        log.info("Expired result cleanup completed. assetsProcessed={}, deleted={}, failed={}, durationMs={}",
                summary.processed(), summary.deleted(), summary.failed(), summary.durationMs());
    }
}
