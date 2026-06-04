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
public class ShadowrocketHandler implements ProtocolHandler {

    @Autowired
    private GeneralHandler generalHandler;

    @Override
    public String getFlag() {
        return "shadowrocket";
    }

    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        if (user == null || servers == null) {
            return "";
        }
        StringBuilder uri = new StringBuilder();
        uri.append(ShadowrocketBuilder.buildStatusLine(user));
        String uuid = user.getUuid();
        for (Map<String, Object> server : servers) {
            if (ShadowrocketBuilder.isVmessServer(server)) {
                uri.append(ShadowrocketBuilder.buildVmess(uuid, server));
            } else {
                uri.append(generalHandler.buildPlainUriForServer(uuid, server));
            }
        }
        return Base64.getEncoder().encodeToString(uri.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void applyResponseHeaders(User user, HttpServletResponse response) {
        SubscribeHeaders.applyUserInfo(response, user);
    }
}
