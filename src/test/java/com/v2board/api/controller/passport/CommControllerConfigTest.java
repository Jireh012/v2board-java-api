package com.v2board.api.controller.passport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.service.ConfigService;
import com.v2board.api.util.Sm4Util;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommControllerConfigTest {

    @Test
    void config_returnsSm4EnvelopeOnly() throws Exception {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getAppName()).thenReturn("DynamicSite");
        when(configService.getStopRegister()).thenReturn(0);
        when(configService.getInviteForce()).thenReturn(1);
        when(configService.getEmailVerify()).thenReturn(1);
        when(configService.getSafeModeEnable()).thenReturn(1);
        when(configService.getSecurePath()).thenReturn("admin888");
        when(configService.getRecaptchaEnable()).thenReturn(1);
        when(configService.getRecaptchaSiteKey()).thenReturn("site-key-public");
        when(configService.getFrontendThemeSidebar()).thenReturn("light");
        when(configService.getFrontendThemeHeader()).thenReturn("dark");
        when(configService.getFrontendThemeColor()).thenReturn("green");
        when(configService.getFrontendBackgroundUrl()).thenReturn("https://cdn.example/bg.jpg");
        when(configService.getTelegramDiscussLink()).thenReturn("https://t.me/example");

        CommController controller = new CommController();
        ReflectionTestUtils.setField(controller, "configService", configService);
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(controller, "sm4Key", "0123456789abcdef");

        ApiResponse<Map<String, String>> resp = controller.config();
        assertEquals(0, resp.getCode());
        assertNotNull(resp.getData());
        assertEquals(2, resp.getData().size());
        assertNotNull(resp.getData().get("iv"));
        assertNotNull(resp.getData().get("payload"));
        assertFalse(resp.getData().containsKey("app_name"));

        byte[] key = Sm4Util.parseKey("0123456789abcdef");
        String json = Sm4Util.decryptFromEnvelope(resp.getData().get("iv"), resp.getData().get("payload"), key);
        Map<String, Object> plain = new ObjectMapper().readValue(json, new TypeReference<>() {});
        assertEquals("DynamicSite", plain.get("app_name"));
        assertEquals(0, ((Number) plain.get("stop_register")).intValue());
        assertEquals(1, ((Number) plain.get("invite_force")).intValue());
        assertEquals(1, ((Number) plain.get("email_verify")).intValue());
        assertEquals(1, ((Number) plain.get("safe_mode_enable")).intValue());
        assertEquals("admin888", plain.get("secure_path"));
        assertEquals(1, ((Number) plain.get("recaptcha_enable")).intValue());
        assertEquals("site-key-public", plain.get("recaptcha_site_key"));
        assertEquals("light", plain.get("frontend_theme_sidebar"));
        assertEquals("dark", plain.get("frontend_theme_header"));
        assertEquals("green", plain.get("frontend_theme_color"));
        assertEquals("https://cdn.example/bg.jpg", plain.get("frontend_background_url"));
        assertEquals("https://t.me/example", plain.get("telegram_discuss_link"));
        assertFalse(plain.containsKey("recaptcha_key"));
        assertEquals(13, plain.size());
    }
}
