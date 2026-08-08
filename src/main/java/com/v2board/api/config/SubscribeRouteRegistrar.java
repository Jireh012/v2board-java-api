package com.v2board.api.config;

import com.v2board.api.controller.ClientController;
import com.v2board.api.service.ConfigService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

/**
 * Registers / refreshes the subscribe HTTP route from {@link ConfigService#getSubscribePath()}.
 * Admin can change {@code site.subscribe_path} without restarting the process.
 */
@Component
@Order(100)
public class SubscribeRouteRegistrar implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(SubscribeRouteRegistrar.class);

    @Autowired
    private ConfigService configService;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private ClientController clientController;

    private final Object lock = new Object();
    private RequestMappingInfo currentMapping;
    private String currentPath;

    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }

    /**
     * Re-read DB/yml subscribe path and rebind the GET handler. No-op if path unchanged.
     */
    public void refresh() {
        synchronized (lock) {
            String path = normalizePath(configService.getSubscribePath());
            if (path.equals(currentPath) && currentMapping != null) {
                return;
            }
            try {
                unregisterCurrent();
                register(path);
                currentPath = path;
                logger.info("Subscribe route active at GET {}", path);
            } catch (Exception e) {
                logger.error("Failed to refresh subscribe route to {}", path, e);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        synchronized (lock) {
            unregisterCurrent();
        }
    }

    private void register(String path) throws Exception {
        Method subscribeMethod = ClientController.class.getMethod(
                "subscribe",
                String.class,
                jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class);

        RequestMappingInfo.BuilderConfiguration options = new RequestMappingInfo.BuilderConfiguration();
        if (handlerMapping.getPatternParser() != null) {
            options.setPatternParser(handlerMapping.getPatternParser());
        }
        if (handlerMapping.getPathMatcher() != null) {
            options.setPathMatcher(handlerMapping.getPathMatcher());
        }

        RequestMappingInfo mappingInfo = RequestMappingInfo
                .paths(path)
                .methods(RequestMethod.GET)
                .produces("text/plain")
                .options(options)
                .build();

        handlerMapping.registerMapping(mappingInfo, clientController, subscribeMethod);
        currentMapping = mappingInfo;
    }

    private void unregisterCurrent() {
        if (currentMapping == null) {
            return;
        }
        try {
            handlerMapping.unregisterMapping(currentMapping);
            logger.info("Unregistered subscribe route GET {}", currentPath);
        } catch (Exception e) {
            logger.warn("Failed to unregister subscribe route {}: {}", currentPath, e.getMessage());
        } finally {
            currentMapping = null;
            currentPath = null;
        }
    }

    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/api/v1/client/subscribe";
        }
        String t = path.trim();
        return t.startsWith("/") ? t : "/" + t;
    }
}
