package com.v2board.api.config;

import com.v2board.api.controller.ClientController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * 动态路由配置
 * 根据配置的订阅路径动态注册路由
 * 使用 ContextRefreshedEvent 确保在 Spring 上下文完全初始化后再注册路由
 */
@Configuration
public class DynamicRouteConfig implements ApplicationListener<ContextRefreshedEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamicRouteConfig.class);
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Value("${v2board.subscribe-path:}")
    private String subscribePath;
    
    private boolean routeRegistered = false;
    
    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        // 避免重复注册（ContextRefreshedEvent 可能会触发多次）
        if (routeRegistered) {
            return;
        }
        
        // 只处理根上下文的事件
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        
        String defaultPath = "/api/v1/client/subscribe";
        
        // 确定要注册的路径：如果配置了自定义路径且不为空，使用自定义路径；否则使用默认路径
        String pathToRegister;
        if (subscribePath != null && !subscribePath.trim().isEmpty()) {
            pathToRegister = subscribePath.trim();
            logger.info("Using configured subscribe path: {}", pathToRegister);
        } else {
            pathToRegister = defaultPath;
            logger.info("No custom subscribe path configured, using default: {}", pathToRegister);
        }
        
        // 动态注册路由
        logger.info("Registering dynamic subscribe route: {}", pathToRegister);
        
        // 延迟注册，确保 Servlet 完全初始化
        new Thread(() -> {
            try {
                // 等待一小段时间，确保 Servlet 完全初始化
                Thread.sleep(100);
                
                RequestMappingHandlerMapping handlerMapping = 
                    applicationContext.getBean(RequestMappingHandlerMapping.class);
                
                ClientController clientController = 
                    applicationContext.getBean(ClientController.class);
                
                // 获取 subscribe 方法
                Method subscribeMethod = ClientController.class.getMethod(
                    "subscribe", 
                    String.class, 
                    jakarta.servlet.http.HttpServletRequest.class
                );
                
                if (subscribeMethod == null) {
                    logger.error("Could not find subscribe method in ClientController");
                    return;
                }
                
                // 确保路径以 / 开头
                String normalizedPath = pathToRegister.startsWith("/") ? pathToRegister : "/" + pathToRegister;
                
                // 创建 RequestMappingInfo
                // 使用与 HandlerMapping 相同的配置
                RequestMappingInfo.BuilderConfiguration options = new RequestMappingInfo.BuilderConfiguration();
                if (handlerMapping.getPatternParser() != null) {
                    options.setPatternParser(handlerMapping.getPatternParser());
                }
                if (handlerMapping.getPathMatcher() != null) {
                    options.setPathMatcher(handlerMapping.getPathMatcher());
                }
                
                RequestMappingInfo mappingInfo = RequestMappingInfo
                    .paths(normalizedPath)
                    .methods(org.springframework.web.bind.annotation.RequestMethod.GET)
                    .produces("text/plain")
                    .options(options)
                    .build();
                
                // 注册路由
                handlerMapping.registerMapping(mappingInfo, clientController, subscribeMethod);
                
                routeRegistered = true;
                logger.info("Successfully registered dynamic subscribe route: {}", normalizedPath);
            } catch (Exception e) {
                logger.error("Error registering dynamic route for path: {}", pathToRegister, e);
                // 如果动态注册失败，记录错误但不阻止应用启动
            }
        }).start();
    }
}

