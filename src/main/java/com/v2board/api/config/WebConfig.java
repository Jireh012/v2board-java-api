package com.v2board.api.config;

import com.v2board.api.middleware.ClientTokenInterceptor;
import com.v2board.api.middleware.ClientAuthInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);

    @Autowired
    private ClientTokenInterceptor clientTokenInterceptor;

    @Autowired
    private ClientAuthInterceptor clientAuthInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // Broad pattern: interceptor itself matches ConfigService.getSubscribePath() at runtime
        // so admin can change site.subscribe_path without restart.
        registry.addInterceptor(clientTokenInterceptor)
                .addPathPatterns("/**");

        registry.addInterceptor(clientAuthInterceptor)
                .addPathPatterns("/api/v1/user/**", "/api/v1/admin/**");

        logger.debug("Registered ClientTokenInterceptor for dynamic subscribe path matching");
    }
}

