package com.v2board.api.protocol;

import com.v2board.api.model.User;
import com.v2board.api.service.ConfigService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ClashNyanpasuHandler implements ProtocolHandler {

    @Autowired
    private ConfigService configService;

    @Override
    public String getFlag() {
        return "nyanpasu";
    }

    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        if (user == null || servers == null || servers.isEmpty()) {
            return "";
        }
        return ClashMetaBuilder.build(servers, user.getUuid(), resolveAppName(), "rules/default.clash.yaml");
    }

    @Override
    public void applyResponseHeaders(User user, HttpServletResponse response) {
        SubscribeHeaders.applyClashMeta(response, user, resolveAppName());
    }

    private String resolveAppName() {
        try {
            Map<String, Object> full = configService.getFullConfig();
            if (full.get("site") instanceof Map<?, ?> m && m.get("app_name") != null) {
                return String.valueOf(m.get("app_name"));
            }
        } catch (Exception ignored) {
        }
        return "V2Board";
    }
}
