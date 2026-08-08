package com.v2board.api.controller.passport;

import com.v2board.api.common.ApiResponse;
import com.v2board.api.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommControllerConfigTest {

    @Test
    void config_returnsPublicBrandAndRegisterFlagsOnly() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getAppName()).thenReturn("DynamicSite");
        when(configService.getStopRegister()).thenReturn(0);
        when(configService.getInviteForce()).thenReturn(1);

        CommController controller = new CommController();
        ReflectionTestUtils.setField(controller, "configService", configService);

        ApiResponse<Map<String, Object>> resp = controller.config();
        assertEquals(0, resp.getCode());
        assertNotNull(resp.getData());
        assertEquals("DynamicSite", resp.getData().get("app_name"));
        assertEquals(0, resp.getData().get("stop_register"));
        assertEquals(1, resp.getData().get("invite_force"));
        assertEquals(3, resp.getData().size());
    }
}
