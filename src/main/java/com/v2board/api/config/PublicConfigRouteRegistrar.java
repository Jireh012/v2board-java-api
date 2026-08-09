package com.v2board.api.config;

import com.v2board.api.controller.passport.CommController;
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
 * Registers public site config at fixed {@link ConfigService#FIXED_PUBLIC_CONFIG_PATH} ({@code /api/config}).
 */
@Component
@Order(105)
public class PublicConfigRouteRegistrar implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(PublicConfigRouteRegistrar.class);

    @Autowired
    private ConfigService configService;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private CommController commController;

    @Autowired(required = false)
    private ClientApiPathRegistry clientApiPathRegistry;

    private final Object lock = new Object();
    private RequestMappingInfo currentMapping;
    private String currentPath;

    @Override
    public void run(ApplicationArguments args) {
        if (clientApiPathRegistry != null) {
            clientApiPathRegistry.refresh();
        }
        refresh();
    }

    public void refresh() {
        synchronized (lock) {
            try {
                // Ensure passport/user/admin prefixes exist; public path is always fixed.
                configService.ensureClientApiPaths();
                String path = ConfigService.FIXED_PUBLIC_CONFIG_PATH;
                if (path.equals(currentPath) && currentMapping != null) {
                    return;
                }
                unregisterCurrent();
                register(path);
                currentPath = path;
                logger.info("Public config route active at GET {}", path);
            } catch (Exception e) {
                logger.error("Failed to refresh public config route", e);
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
        Method method = CommController.class.getMethod("config");

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
                .options(options)
                .build();

        handlerMapping.registerMapping(mappingInfo, commController, method);
        currentMapping = mappingInfo;
    }

    private void unregisterCurrent() {
        if (currentMapping == null) {
            return;
        }
        try {
            handlerMapping.unregisterMapping(currentMapping);
            logger.info("Unregistered public config route GET {}", currentPath);
        } catch (Exception e) {
            logger.warn("Failed to unregister public config route {}: {}", currentPath, e.getMessage());
        } finally {
            currentMapping = null;
            currentPath = null;
        }
    }
}
