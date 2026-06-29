package com.v2board.api.protocol;

import com.v2board.api.model.User;
import com.v2board.api.service.ConfigService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ClashVergeHandler implements ProtocolHandler {

    @Autowired
    private ConfigService configService;

    @Override
    public String getFlag() {
        return "verge";
    }

    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        if (user == null || servers == null || servers.isEmpty()) {
            return "";
        }
        return ClashMetaBuilder.build(servers, user.getUuid(), configService.getAppName(), "rules/default.clash.yaml");
    }

    @Override
    public void applyResponseHeaders(User user, HttpServletResponse response) {
        SubscribeHeaders.applyClashMeta(response, user, configService.getAppName());
    }
}
