package com.v2board.api.controller.passport;

import com.v2board.api.common.ApiResponse;
import com.v2board.api.service.ConfigService;
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
    void config_returnsPlainPublicFieldsIncludingPrefixes() throws Exception {
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
        when(configService.ensureClientApiPaths()).thenReturn(Map.of(
                "passport_api_prefix", "/p/abcdefghijkl",
                "user_api_prefix", "/u/mnopqrstuvwx",
                "admin_api_prefix", "/a/zyxwvutsrqpo",
                "public_config_path", ConfigService.FIXED_PUBLIC_CONFIG_PATH
        ));

        CommController controller = new CommController();
        ReflectionTestUtils.setField(controller, "configService", configService);

        ApiResponse<Map<String, Object>> resp = controller.config();
        assertEquals(0, resp.getCode());
        assertNotNull(resp.getData());
        Map<String, Object> data = resp.getData();
        assertFalse(data.containsKey("iv"));
        assertFalse(data.containsKey("payload"));
        assertEquals("DynamicSite", data.get("app_name"));
        assertEquals(0, ((Number) data.get("stop_register")).intValue());
        assertEquals(1, ((Number) data.get("invite_force")).intValue());
        assertEquals(1, ((Number) data.get("email_verify")).intValue());
        assertEquals(1, ((Number) data.get("safe_mode_enable")).intValue());
        assertEquals("admin888", data.get("secure_path"));
        assertEquals(1, ((Number) data.get("recaptcha_enable")).intValue());
        assertEquals("site-key-public", data.get("recaptcha_site_key"));
        assertEquals("light", data.get("frontend_theme_sidebar"));
        assertEquals("dark", data.get("frontend_theme_header"));
        assertEquals("green", data.get("frontend_theme_color"));
        assertEquals("https://cdn.example/bg.jpg", data.get("frontend_background_url"));
        assertEquals("https://t.me/example", data.get("telegram_discuss_link"));
        assertEquals("/p/abcdefghijkl", data.get("passport_api_prefix"));
        assertEquals("/u/mnopqrstuvwx", data.get("user_api_prefix"));
        assertEquals("/a/zyxwvutsrqpo", data.get("admin_api_prefix"));
        assertEquals("/config", data.get("public_config_path"));
        assertFalse(data.containsKey("recaptcha_key"));
        assertEquals(17, data.size());
    }
}
