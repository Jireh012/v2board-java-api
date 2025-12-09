package com.v2board.api.config;

import com.v2board.api.middleware.ClientTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Autowired
    private ClientTokenInterceptor clientTokenInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 客户端订阅接口拦截器
        registry.addInterceptor(clientTokenInterceptor)
            .addPathPatterns("/api/v1/client/**");
    }
}

