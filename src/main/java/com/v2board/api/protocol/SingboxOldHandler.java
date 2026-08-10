package com.v2board.api.protocol;

import com.v2board.api.model.User;
import com.v2board.api.service.ConfigService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * sing-box &lt; 1.12.0 使用旧版配置模板（本地 geosite/geoip，无远程 rule_set）。
 * 管理端自定义模板仅覆盖 ≥1.12 的 {@link SingboxHandler}；旧客户端固定读 classpath 种子。
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
        if (user == null || servers == null || servers.isEmpty()) {
            return "{}";
        }
        String resource = new ClassPathResource("rules/default.sing-box.old.json").exists()
                ? "rules/default.sing-box.old.json"
                : "rules/default.sing-box.json";
        String template = loadClasspath(resource);
        if (template != null && !template.isBlank()) {
            return SingboxBuilder.buildFromContent(user, servers, template, false);
        }
        return SingboxBuilder.build(user, servers, resource, false);
    }

    @Override
    public void applyResponseHeaders(User user, HttpServletResponse response) {
        SubscribeHeaders.applySingbox(response, user, configService.getAppName());
    }

    private static String loadClasspath(String path) {
        try {
            ClassPathResource res = new ClassPathResource(path);
            if (!res.exists()) {
                return null;
            }
            return StreamUtils.copyToString(res.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
