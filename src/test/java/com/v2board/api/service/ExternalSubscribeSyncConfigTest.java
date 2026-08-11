package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.config.ExternalSubscribeProperties;
import com.v2board.api.mapper.SystemConfigMapper;
import com.v2board.api.schedule.ExternalSubscribeSyncScheduler;
import com.v2board.api.service.external.ExternalSyncSettings;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalSubscribeSyncConfigTest {

    @Test
    void validate_rejectsInvalidCronWhenEnabled() {
        Map<String, Object> body = subscribeBody(map -> {
            map.put("external_sync_enable", 1);
            map.put("external_sync_mode", "cron");
            map.put("external_sync_cron", "not-a-cron");
        });
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ConfigService.validateExternalSyncInSaveBody(body));
        assertTrue(ex.getMessage().contains("cron"));
    }

    @Test
    void validate_rejectsDashCronAsDisable() {
        Map<String, Object> body = subscribeBody(map -> {
            map.put("external_sync_enable", 1);
            map.put("external_sync_mode", "cron");
            map.put("external_sync_cron", "-");
        });
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ConfigService.validateExternalSyncInSaveBody(body));
        assertTrue(ex.getMessage().contains("开关"));
    }

    @Test
    void validate_rejectsInvalidIntervalUnitAndZeroValue() {
        Map<String, Object> zero = subscribeBody(map -> {
            map.put("external_sync_enable", 1);
            map.put("external_sync_mode", "interval");
            map.put("external_sync_interval_value", 0);
            map.put("external_sync_interval_unit", "minute");
        });
        BusinessException z = assertThrows(BusinessException.class,
                () -> ConfigService.validateExternalSyncInSaveBody(zero));
        assertTrue(z.getMessage().contains("正整数"));

        Map<String, Object> unit = subscribeBody(map -> {
            map.put("external_sync_enable", 1);
            map.put("external_sync_mode", "interval");
            map.put("external_sync_interval_value", 5);
            map.put("external_sync_interval_unit", "week");
        });
        BusinessException u = assertThrows(BusinessException.class,
                () -> ConfigService.validateExternalSyncInSaveBody(unit));
        assertTrue(u.getMessage().contains("单位"));
    }

    @Test
    void validate_rejectsIntervalAboveCap() {
        Map<String, Object> body = subscribeBody(map -> {
            map.put("external_sync_enable", 1);
            map.put("external_sync_mode", "interval");
            map.put("external_sync_interval_value", 31);
            map.put("external_sync_interval_unit", "day");
        });
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ConfigService.validateExternalSyncInSaveBody(body));
        assertTrue(ex.getMessage().contains("上限"));
    }

    @Test
    void validate_acceptsValidIntervalAndCron() {
        Map<String, Object> interval = subscribeBody(map -> {
            map.put("external_sync_enable", 1);
            map.put("external_sync_mode", "interval");
            map.put("external_sync_interval_value", 15);
            map.put("external_sync_interval_unit", "minute");
        });
        assertDoesNotThrow(() -> ConfigService.validateExternalSyncInSaveBody(interval));

        Map<String, Object> cron = subscribeBody(map -> {
            map.put("external_sync_enable", 1);
            map.put("external_sync_mode", "cron");
            map.put("external_sync_cron", "0 */10 * * * *");
        });
        assertDoesNotThrow(() -> ConfigService.validateExternalSyncInSaveBody(cron));
    }

    @Test
    void validate_enableOffSkipsStrictIntervalChecks() {
        Map<String, Object> body = subscribeBody(map -> {
            map.put("external_sync_enable", 0);
            map.put("external_sync_mode", "interval");
            map.put("external_sync_interval_value", 0);
            map.put("external_sync_interval_unit", "minute");
        });
        assertDoesNotThrow(() -> ConfigService.validateExternalSyncInSaveBody(body));
    }

    @Test
    void buildDefaults_enableOffWhenYmlCronIsDash() throws Exception {
        ConfigService svc = newConfigService(mock(SystemConfigMapper.class));
        ExternalSubscribeProperties props = new ExternalSubscribeProperties();
        props.setCron("-");
        ReflectionTestUtils.setField(svc, "externalSubscribeProperties", props);

        @SuppressWarnings("unchecked")
        Map<String, Object> defaults = (Map<String, Object>) ReflectionTestUtils.invokeMethod(svc, "buildDefaults");
        @SuppressWarnings("unchecked")
        Map<String, Object> subscribe = (Map<String, Object>) defaults.get("subscribe");
        assertEquals(0, subscribe.get("external_sync_enable"));
        assertEquals(ExternalSyncSettings.DEFAULT_CRON, subscribe.get("external_sync_cron"));
    }

    @Test
    void buildDefaults_seedsCronModeFromNonDefaultEnvCron() throws Exception {
        ConfigService svc = newConfigService(mock(SystemConfigMapper.class));
        ExternalSubscribeProperties props = new ExternalSubscribeProperties();
        props.setCron("0 0 */2 * * *");
        ReflectionTestUtils.setField(svc, "externalSubscribeProperties", props);

        @SuppressWarnings("unchecked")
        Map<String, Object> defaults = (Map<String, Object>) ReflectionTestUtils.invokeMethod(svc, "buildDefaults");
        @SuppressWarnings("unchecked")
        Map<String, Object> subscribe = (Map<String, Object>) defaults.get("subscribe");
        assertEquals(1, subscribe.get("external_sync_enable"));
        assertEquals("cron", subscribe.get("external_sync_mode"));
        assertEquals("0 0 */2 * * *", subscribe.get("external_sync_cron"));
    }

    @Test
    void save_persistsExternalSyncAndAcceptsValidBody() throws Exception {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        ConfigService svc = newConfigService(mapper);
        ExternalSubscribeProperties props = new ExternalSubscribeProperties();
        props.setCron("-");
        ReflectionTestUtils.setField(svc, "externalSubscribeProperties", props);
        ExternalSubscribeSyncScheduler scheduler = mock(ExternalSubscribeSyncScheduler.class);
        ReflectionTestUtils.setField(svc, "externalSubscribeSyncScheduler", scheduler);

        Map<String, Object> body = subscribeBody(map -> {
            map.put("external_sync_enable", 1);
            map.put("external_sync_mode", "interval");
            map.put("external_sync_interval_value", 5);
            map.put("external_sync_interval_unit", "minute");
        });
        assertDoesNotThrow(() -> svc.save(body));
        verify(scheduler).rescheduleFromConfig();
    }

    @Test
    void save_withoutSubscribe_doesNotReschedule() throws Exception {
        SystemConfigMapper mapper = mock(SystemConfigMapper.class);
        when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        ConfigService svc = newConfigService(mapper);
        ExternalSubscribeProperties props = new ExternalSubscribeProperties();
        props.setCron("-");
        ReflectionTestUtils.setField(svc, "externalSubscribeProperties", props);
        ExternalSubscribeSyncScheduler scheduler = mock(ExternalSubscribeSyncScheduler.class);
        ReflectionTestUtils.setField(svc, "externalSubscribeSyncScheduler", scheduler);

        Map<String, Object> body = new HashMap<>();
        body.put("ticket", Map.of("ticket_status", 0));
        assertDoesNotThrow(() -> svc.save(body));
        verify(scheduler, never()).rescheduleFromConfig();
    }

    @FunctionalInterface
    private interface SubMutator {
        void accept(Map<String, Object> sub);
    }

    private static Map<String, Object> subscribeBody(SubMutator mutator) {
        Map<String, Object> sub = new HashMap<>();
        mutator.accept(sub);
        Map<String, Object> body = new HashMap<>();
        body.put("subscribe", sub);
        return body;
    }

    private static ConfigService newConfigService(SystemConfigMapper mapper) {
        ConfigService configService = new ConfigService();
        ReflectionTestUtils.setField(configService, "systemConfigMapper", mapper);
        ReflectionTestUtils.setField(configService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(configService, "appName", "V2Board");
        ReflectionTestUtils.setField(configService, "phpConfigPath", "");
        ReflectionTestUtils.setField(configService, "appUrl", "");
        ReflectionTestUtils.setField(configService, "subscribeUrl", "");
        ReflectionTestUtils.setField(configService, "subscribePath", "/api/v1/client/subscribe");
        ReflectionTestUtils.setField(configService, "showSubscribeMethod", 0);
        ReflectionTestUtils.setField(configService, "showSubscribeExpire", 5);
        ReflectionTestUtils.setField(configService, "allowNewPeriod", 0);
        ReflectionTestUtils.setField(configService, "resetTrafficMethod", 0);
        ReflectionTestUtils.setField(configService, "showInfoToServerEnable", false);
        ReflectionTestUtils.setField(configService, "inviteCommission", 10);
        ReflectionTestUtils.setField(configService, "inviteGenLimit", 5);
        ReflectionTestUtils.setField(configService, "ticketStatus", 0);
        ReflectionTestUtils.setField(configService, "withdrawCloseEnable", 0);
        ReflectionTestUtils.setField(configService, "commissionWithdrawLimit", 100);
        ReflectionTestUtils.setField(configService, "commissionDistributionEnable", 0);
        ReflectionTestUtils.setField(configService, "commissionDistributionL1", 100.0);
        return configService;
    }
}
