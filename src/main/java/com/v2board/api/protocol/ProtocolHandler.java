package com.v2board.api.protocol;

import com.v2board.api.model.User;
import java.util.List;
import java.util.Map;

/**
 * 协议处理器接口
 */
public interface ProtocolHandler {
    /**
     * 处理订阅请求，生成订阅内容
     * @param user 用户信息
     * @param servers 服务器列表
     * @return 订阅内容
     */
    String handle(User user, List<Map<String, Object>> servers);
    
    /**
     * 获取协议标识（用于匹配 User-Agent）
     */
    String getFlag();
}

