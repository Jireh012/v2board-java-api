package com.v2board.api.config;

import com.v2board.api.middleware.ClientTokenInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);
    
    @Autowired
    private ClientTokenInterceptor clientTokenInterceptor;
    
    @Value("${v2board.subscribe-path:}")
    private String subscribePath;
    
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // 客户端订阅接口拦截器
        String defaultPath = "/api/v1/client/subscribe";
        
        // 确定要拦截的路径
        String pathToIntercept;
        if (subscribePath != null && !subscribePath.trim().isEmpty()) {
            pathToIntercept = subscribePath.trim();
        } else {
            pathToIntercept = defaultPath;
        }
        
        // 确保路径以 / 开头
        if (!pathToIntercept.startsWith("/")) {
            pathToIntercept = "/" + pathToIntercept;
        }
        
        // 拦截配置的订阅路径
        registry.addInterceptor(clientTokenInterceptor)
            .addPathPatterns(pathToIntercept);
        
        logger.debug("Registered interceptor for subscribe path: {}", pathToIntercept);
    }
}

