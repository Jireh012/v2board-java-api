package com.v2board.api.schedule;

import com.v2board.api.service.ConfigService;
import com.v2board.api.service.external.ExternalSubscribeSyncService;
import com.v2board.api.service.external.ExternalSyncSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dynamically schedules external-subscribe {@code syncAll} from system config
 * ({@code subscribe.external_sync_*}). Replaces fixed {@code @Scheduled} cron.
 */
@Component
public class ExternalSubscribeSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ExternalSubscribeSyncScheduler.class);

    private final ThreadPoolTaskScheduler taskScheduler;
    private final ConfigService configService;
    private final ExternalSubscribeSyncService syncService;
    private final AtomicReference<ScheduledFuture<?>> current = new AtomicReference<>();

    public ExternalSubscribeSyncScheduler(ThreadPoolTaskScheduler taskScheduler,
                                          ConfigService configService,
                                          ExternalSubscribeSyncService syncService) {
        this.taskScheduler = taskScheduler;
        this.configService = configService;
        this.syncService = syncService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        rescheduleFromConfig();
    }

    /**
     * Cancel previous task (if any) and register according to merged config.
     * enable=0 → no schedule. Interval uses fixedDelay (after completion).
     */
    public void rescheduleFromConfig() {
        cancelCurrent();
        ExternalSyncSettings settings;
        try {
            settings = configService.getExternalSyncSettings();
        } catch (Exception e) {
            logger.error("Failed to read external sync settings; auto-sync disabled", e);
            return;
        }
        if (!settings.enabled()) {
            logger.info("External subscribe auto-sync disabled");
            return;
        }
        try {
            ScheduledFuture<?> future;
            if (settings.isCronMode()) {
                CronTrigger trigger = new CronTrigger(settings.cron());
                future = taskScheduler.schedule(this::runSync, trigger);
                logger.info("External subscribe auto-sync scheduled with cron={}", settings.cron());
            } else {
                Duration period = settings.intervalDuration();
                PeriodicTrigger trigger = new PeriodicTrigger(period);
                trigger.setFixedRate(false);
                // Avoid immediate syncAll on every boot/reschedule; first run after one period.
                trigger.setInitialDelay(period);
                future = taskScheduler.schedule(this::runSync, trigger);
                logger.info("External subscribe auto-sync scheduled every {} {} (fixedDelay)",
                        settings.intervalValue(), settings.intervalUnit());
            }
            current.set(future);
        } catch (Exception e) {
            logger.error("Failed to schedule external subscribe auto-sync", e);
        }
    }

    /** Visible for tests: whether a future is currently held. */
    boolean hasScheduledTask() {
        ScheduledFuture<?> f = current.get();
        return f != null && !f.isCancelled();
    }

    void cancelCurrent() {
        ScheduledFuture<?> prev = current.getAndSet(null);
        if (prev != null) {
            prev.cancel(false);
        }
    }

    private void runSync() {
        try {
            logger.info("ExternalSubscribeSyncScheduler start");
            syncService.syncAll();
        } catch (Exception e) {
            logger.error("ExternalSubscribeSyncScheduler failed", e);
        }
    }
}
