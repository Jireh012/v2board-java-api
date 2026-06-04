package com.v2board.api.protocol;

import com.v2board.api.model.User;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class LoonHandler implements ProtocolHandler {

    @Override
    public String getFlag() {
        return "loon";
    }

    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        return LoonBuilder.build(servers, user.getUuid());
    }

    @Override
    public void applyResponseHeaders(User user, HttpServletResponse response) {
        SubscribeHeaders.applyLoon(response, user);
    }
}
