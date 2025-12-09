package com.v2board.api.protocol;

import com.v2board.api.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Clash 协议处理器
 * 注意：这是一个简化版本，完整实现需要生成 YAML 格式的 Clash 配置
 * 可以参考 PHP 版本的 Clash.php 实现完整的 YAML 生成逻辑
 */
@Component
public class ClashHandler implements ProtocolHandler {
    
    @Autowired
    private GeneralHandler generalHandler;
    
    @Override
    public String getFlag() {
        return "clash";
    }
    
    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        // Clash 需要生成 YAML 格式的配置
        // 这里简化处理，实际应该读取模板文件并填充数据
        // 可以参考 PHP 版本的 Clash.php 实现
        // 暂时使用 General 处理器生成基础订阅
        return generalHandler.handle(user, servers);
    }
}

