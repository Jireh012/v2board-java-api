package com.v2board.api.schedule;

import com.v2board.api.service.external.ExternalSubscribeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExternalSubscribeSchedule {

    private static final Logger logger = LoggerFactory.getLogger(ExternalSubscribeSchedule.class);

    private final ExternalSubscribeSyncService syncService;

    public ExternalSubscribeSchedule(ExternalSubscribeSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(cron = "${v2board.external-subscribe.cron:0 */30 * * * *}")
    public void syncExternalSubscriptions() {
        try {
            logger.info("ExternalSubscribeSchedule start");
            syncService.syncAll();
        } catch (Exception e) {
            logger.error("ExternalSubscribeSchedule failed", e);
        }
    }
}
