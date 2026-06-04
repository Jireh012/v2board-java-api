package com.v2board.api.protocol;

import com.v2board.api.model.User;
import com.v2board.api.service.ConfigService;
import com.v2board.api.util.Helper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class SurgeHandler implements ProtocolHandler {

    @Autowired
    private ConfigService configService;

    @Value("${v2board.show-subscribe-method:0}")
    private Integer subscribeMethod;

    @Value("${v2board.subscribe-path:}")
    private String subscribePath;

    @Value("${v2board.subscribe-url:}")
    private String subscribeUrlConfig;

    @Value("${v2board.show-subscribe-expire:5}")
    private Integer subscribeExpire;

    @Override
    public String getFlag() {
        return "surge";
    }

    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        if (user == null || servers == null) {
            return "";
        }
        String appName = resolveAppName();
        String subsLink = Helper.getSubscribeUrl(
                user.getToken(), user.getId(), subscribeMethod, subscribePath, subscribeUrlConfig, subscribeExpire);
        String subsDomain = resolveHost();
        return SurgeBuilder.build(servers, user, appName, subsLink, subsDomain);
    }

    @Override
    public void applyResponseHeaders(User user, HttpServletResponse response) {
        SubscribeHeaders.applyUserInfo(response, user);
        if (response == null) {
            return;
        }
        String appName = resolveAppName();
        response.setHeader("content-disposition",
                "attachment;filename*=UTF-8''" + java.net.URLEncoder.encode(appName, StandardCharsets.UTF_8) + ".conf");
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

    private static String resolveHost() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null && attrs.getRequest() != null) {
            return attrs.getRequest().getServerName();
        }
        return "";
    }
}
