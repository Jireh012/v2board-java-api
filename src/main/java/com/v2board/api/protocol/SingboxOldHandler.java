package com.v2board.api.protocol;

import com.v2board.api.model.User;
import com.v2board.api.service.ConfigService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * sing-box &lt; 1.12.0 使用旧版配置模板
 */
@Component
public class SingboxOldHandler implements ProtocolHandler {

    @Autowired
    private ConfigService configService;

    @Override
    public String getFlag() {
        return "sing";
    }

    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        String template = new ClassPathResource("rules/default.sing-box.old.json").exists()
                ? "rules/default.sing-box.old.json"
                : "rules/default.sing-box.json";
        return SingboxBuilder.build(user, servers, template, false);
    }

    @Override
    public void applyResponseHeaders(User user, HttpServletResponse response) {
        SubscribeHeaders.applySingbox(response, user, configService.getAppName());
    }
}
