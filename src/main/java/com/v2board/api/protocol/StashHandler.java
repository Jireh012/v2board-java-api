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
 * 对齐 PHP App\Protocols\Stash（使用 stash 或 clash 规则模板）
 */
@Component
public class StashHandler implements ProtocolHandler {

    @Autowired
    private ConfigService configService;

    @Override
    public String getFlag() {
        return "stash";
    }

    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        if (user == null || servers == null || servers.isEmpty()) {
            return "";
        }
        String template = new ClassPathResource("rules/default.stash.yaml").exists()
                ? "rules/default.stash.yaml"
                : "rules/default.clash.yaml";
        return ClashMetaBuilder.build(servers, user.getUuid(), configService.getAppName(), template);
    }

    @Override
    public void applyResponseHeaders(User user, HttpServletResponse response) {
        SubscribeHeaders.applyClashMeta(response, user, configService.getAppName());
    }
}
