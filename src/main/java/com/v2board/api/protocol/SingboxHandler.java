package com.v2board.api.protocol;

import com.v2board.api.model.User;
import com.v2board.api.service.ConfigService;
import com.v2board.api.service.RuleTemplateService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SingboxHandler implements ProtocolHandler {

    @Autowired
    private ConfigService configService;

    @Autowired
    private RuleTemplateService ruleTemplateService;

    @Override
    public String getFlag() {
        return "sing";
    }

    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        if (user == null || servers == null || servers.isEmpty()) {
            return "{}";
        }
        String template = ruleTemplateService.resolve("singbox");
        return SingboxBuilder.buildFromContent(user, servers, template, true);
    }

    @Override
    public void applyResponseHeaders(User user, HttpServletResponse response) {
        SubscribeHeaders.applySingbox(response, user, configService.getAppName());
    }
}
