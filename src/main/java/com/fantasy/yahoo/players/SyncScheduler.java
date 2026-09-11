package com.fantasy.yahoo.players;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes the cached player read model on a schedule. The cron is always registered
 * (default daily 04:00) but no-ops unless sync.schedule.enabled=true (enabled in the deployed
 * envs, off locally / in CI), and is skipped while sync.disabled=true — the off-season kill
 * switch that keeps the daily Yahoo 403 out of the logs and Sentry.
 */
@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final SyncService syncService;
    private final boolean enabled;
    private final boolean disabled;

    public SyncScheduler(SyncService syncService,
                         @Value("${sync.schedule.enabled:false}") boolean enabled,
                         @Value("${sync.disabled:false}") boolean disabled) {
        this.syncService = syncService;
        this.enabled = enabled;
        this.disabled = disabled;
    }

    @Scheduled(cron = "${sync.schedule.cron:0 0 4 * * *}")
    public void scheduledSync() {
        if (!enabled || disabled) {
            return;
        }
        try {
            if (syncService.sync().isEmpty()) {
                log.info("Scheduled player sync skipped: a triggered sync is already running");
            }
        } catch (Exception e) {
            log.error("Scheduled player sync failed", e);
        }
    }
}
