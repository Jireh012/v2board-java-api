package com.v2board.api.schedule;

import com.v2board.api.service.ConfigService;
import com.v2board.api.service.external.ExternalSubscribeSyncService;
import com.v2board.api.service.external.ExternalSyncSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalSubscribeSyncSchedulerTest {

    @Mock ThreadPoolTaskScheduler taskScheduler;
    @Mock ConfigService configService;
    @Mock ExternalSubscribeSyncService syncService;
    @Mock ScheduledFuture<?> future;

    ExternalSubscribeSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ExternalSubscribeSyncScheduler(taskScheduler, configService, syncService);
    }

    @Test
    void reschedule_enableOff_doesNotSchedule() {
        when(configService.getExternalSyncSettings()).thenReturn(
                new ExternalSyncSettings(false, "interval", 30, "minute",
                        ExternalSyncSettings.DEFAULT_CRON));

        scheduler.rescheduleFromConfig();

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
        assertFalse(scheduler.hasScheduledTask());
    }

    @Test
    void reschedule_enableOff_cancelsPreviousTask() {
        when(configService.getExternalSyncSettings()).thenReturn(
                new ExternalSyncSettings(true, "interval", 30, "minute",
                        ExternalSyncSettings.DEFAULT_CRON));
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn((ScheduledFuture) future);

        scheduler.rescheduleFromConfig();
        assertTrue(scheduler.hasScheduledTask());

        when(configService.getExternalSyncSettings()).thenReturn(
                new ExternalSyncSettings(false, "interval", 30, "minute",
                        ExternalSyncSettings.DEFAULT_CRON));
        scheduler.rescheduleFromConfig();

        verify(future).cancel(false);
        assertFalse(scheduler.hasScheduledTask());
        // only the first (enabled) reschedule should have scheduled
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void reschedule_intervalMode_usesPeriodicTriggerFixedDelay() {
        when(configService.getExternalSyncSettings()).thenReturn(
                new ExternalSyncSettings(true, "interval", 5, "minute",
                        ExternalSyncSettings.DEFAULT_CRON));
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn((ScheduledFuture) future);

        scheduler.rescheduleFromConfig();

        ArgumentCaptor<Trigger> cap = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(any(Runnable.class), cap.capture());
        assertInstanceOf(PeriodicTrigger.class, cap.getValue());
        PeriodicTrigger pt = (PeriodicTrigger) cap.getValue();
        assertFalse(pt.isFixedRate());
        // Spec: initial delay = period so boot/reschedule does not fire syncAll immediately.
        assertEquals(java.time.Duration.ofMinutes(5), pt.getInitialDelayDuration());
        assertEquals(java.time.Duration.ofMinutes(5), pt.getPeriodDuration());
        assertTrue(scheduler.hasScheduledTask());
    }

    @Test
    void reschedule_cronMode_usesCronTrigger() {
        when(configService.getExternalSyncSettings()).thenReturn(
                new ExternalSyncSettings(true, "cron", 30, "minute", "0 */15 * * * *"));
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn((ScheduledFuture) future);

        scheduler.rescheduleFromConfig();

        ArgumentCaptor<Trigger> cap = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(any(Runnable.class), cap.capture());
        assertInstanceOf(CronTrigger.class, cap.getValue());
    }
}
