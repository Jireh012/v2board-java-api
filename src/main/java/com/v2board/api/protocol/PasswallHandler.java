package com.v2board.api.protocol;

import com.v2board.api.model.User;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class PasswallHandler implements ProtocolHandler {

    @Autowired
    private GeneralHandler generalHandler;

    @Override
    public String getFlag() {
        return "passwall";
    }

    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        String plain = generalHandler.buildPlainUriContent(user, servers);
        if (plain.isEmpty()) {
            return "";
        }
        return Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void applyResponseHeaders(User user, HttpServletResponse response) {
        SubscribeHeaders.applyUserInfo(response, user);
    }
}
