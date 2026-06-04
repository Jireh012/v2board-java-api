package com.v2board.api.protocol;

import com.v2board.api.model.User;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

/**
 * 协议处理器接口
 */
public interface ProtocolHandler {
    String handle(User user, List<Map<String, Object>> servers);

    String getFlag();

    /**
     * 设置订阅响应头（默认仅 subscription-userinfo）
     */
    default void applyResponseHeaders(User user, HttpServletResponse response) {
        if (response != null && user != null) {
            SubscribeHeaders.applyUserInfo(response, user);
        }
    }
}

