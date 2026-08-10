package com.v2board.api.protocol;

import com.v2board.api.model.User;
import com.v2board.api.service.ConfigService;
import com.v2board.api.service.RuleTemplateService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class QuantumultXHandler implements ProtocolHandler {

    @Autowired
    private ConfigService configService;

    @Autowired
    private RuleTemplateService ruleTemplateService;

    @Override
    public String getFlag() {
        return "quantumult%20x";
    }

    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        if (user == null || servers == null) {
            return "";
        }
        String template = ruleTemplateService.resolve("quantumultx");
        return QuantumultXBuilder.buildFromContent(servers, user.getUuid(), resolveHost(), template);
    }

    @Override
    public void applyResponseHeaders(User user, HttpServletResponse response) {
        SubscribeHeaders.applyUserInfo(response, user);
        if (response == null) {
            return;
        }
        String appName = configService.getAppName();
        response.setHeader("content-disposition",
                "attachment;filename*=UTF-8''" + java.net.URLEncoder.encode(appName, StandardCharsets.UTF_8) + ".conf");
    }

    private static String resolveHost() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null && attrs.getRequest() != null) {
            return attrs.getRequest().getServerName();
        }
        return "";
    }
}
