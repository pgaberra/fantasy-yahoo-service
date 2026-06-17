package com.fantasy.yahoo.players;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes the cached player read model on a schedule. The cron is always registered
 * (default daily 04:00) but no-ops unless sync.schedule.enabled=true — enabled in the
 * deployed envs, off locally / in CI.
 */
@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final SyncService syncService;
    private final boolean enabled;

    public SyncScheduler(SyncService syncService,
                         @Value("${sync.schedule.enabled:false}") boolean enabled) {
        this.syncService = syncService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${sync.schedule.cron:0 0 4 * * *}")
    public void scheduledSync() {
        if (!enabled) {
            return;
        }
        try {
            syncService.sync();
        } catch (Exception e) {
            log.error("Scheduled player sync failed", e);
        }
    }
}
