package com.v2board.api.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.model.User;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shadowsocks 官方客户端 SIP008 订阅（对齐 PHP App\Protocols\Shadowsocks）
 */
@Component
public class ShadowsocksSipHandler implements ProtocolHandler {

    private static final Set<String> ALLOWED_CIPHERS = Set.of(
            "aes-128-gcm", "aes-256-gcm", "aes-192-gcm", "chacha20-ietf-poly1305");

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getFlag() {
        return "shadowsocks";
    }

    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        if (user == null || servers == null) {
            return "{}";
        }
        long bytesUsed = (user.getU() != null ? user.getU() : 0) + (user.getD() != null ? user.getD() : 0);
        long total = user.getTransferEnable() != null ? user.getTransferEnable() : 0;
        long bytesRemaining = total - bytesUsed;

        List<Map<String, Object>> configs = new ArrayList<>();
        for (Map<String, Object> item : servers) {
            if (!"shadowsocks".equals(str(item.get("type")))) {
                continue;
            }
            String cipher = str(item.get("cipher"));
            if (!ALLOWED_CIPHERS.contains(cipher)) {
                continue;
            }
            configs.add(sip008(item, user));
        }

        Map<String, Object> subs = new LinkedHashMap<>();
        subs.put("version", 1);
        subs.put("bytes_used", bytesUsed);
        subs.put("bytes_remaining", bytesRemaining);
        subs.put("servers", configs);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(subs);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static Map<String, Object> sip008(Map<String, Object> server, User user) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("id", server.get("id"));
        config.put("remarks", server.get("name"));
        config.put("server", server.get("host"));
        config.put("server_port", server.get("port"));
        config.put("password", user.getUuid());
        config.put("method", server.get("cipher"));
        return config;
    }

    @Override
    public void applyResponseHeaders(User user, HttpServletResponse response) {
        SubscribeHeaders.applyUserInfo(response, user);
        if (response != null) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
