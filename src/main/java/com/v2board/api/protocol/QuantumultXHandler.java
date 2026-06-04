package com.v2board.api.protocol;

import com.v2board.api.model.User;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class QuantumultXHandler implements ProtocolHandler {

    @Override
    public String getFlag() {
        return "quantumult%20x";
    }

    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        if (user == null || servers == null) {
            return "";
        }
        String plain = QuantumultXBuilder.buildPlain(servers, user.getUuid());
        return Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void applyResponseHeaders(User user, HttpServletResponse response) {
        SubscribeHeaders.applyUserInfo(response, user);
    }
}
