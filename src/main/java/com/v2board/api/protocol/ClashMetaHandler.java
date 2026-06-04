package com.v2board.api.protocol;

import com.v2board.api.model.User;
import com.v2board.api.service.ConfigService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Clash Meta / Clash Verge / Nyanpasu 等（flag 含 meta）
 */
@Component
public class ClashMetaHandler implements ProtocolHandler {

    @Autowired
    private ConfigService configService;

    @Override
    public String getFlag() {
        return "meta";
    }

    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        if (user == null || servers == null || servers.isEmpty()) {
            return "";
        }
        String appName = "V2Board";
        try {
            Map<String, Object> full = configService.getFullConfig();
            Object site = full.get("site");
            if (site instanceof Map<?, ?> m && m.get("app_name") != null) {
                appName = String.valueOf(m.get("app_name"));
            }
        } catch (Exception ignored) {
        }
        return ClashMetaBuilder.build(servers, user.getUuid(), appName, "rules/default.clash.yaml");
    }

    @Override
    public void applyResponseHeaders(User user, HttpServletResponse response) {
        String appName = "V2Board";
        try {
            Map<String, Object> full = configService.getFullConfig();
            if (full.get("site") instanceof Map<?, ?> m && m.get("app_name") != null) {
                appName = String.valueOf(m.get("app_name"));
            }
        } catch (Exception ignored) {
        }
        SubscribeHeaders.applyClashMeta(response, user, appName);
    }
}
