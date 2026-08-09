package com.v2board.api.config;

import com.v2board.api.controller.server.UniProxyController;
import com.v2board.api.service.ConfigService;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registers / refreshes obfuscated node API routes {@code {server_api_prefix}/{c,u,p,a,l}}.
 */
@Component
@Order(110)
public class NodeApiRouteRegistrar implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(NodeApiRouteRegistrar.class);

    private static final String[] ACTIONS = {"c", "u", "p", "a", "l"};
    private static final String[] METHODS = {"config", "user", "push", "alive", "alivelist"};

    @Autowired
    private ConfigService configService;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private UniProxyController uniProxyController;

    private final Object lock = new Object();
    private final List<RequestMappingInfo> currentMappings = new ArrayList<>();
    private String currentPrefix;

    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }

    /**
     * Re-read {@code server.server_api_prefix} and rebind handlers. Ensures prefix exists (auto-gen + persist).
     */
    public void refresh() {
        synchronized (lock) {
            try {
                String path = configService.ensureServerApiPrefix();
                if (!StringUtils.hasText(path)) {
                    logger.error("Node API prefix empty after ensure; routes not registered");
                    unregisterCurrent();
                    return;
                }
                path = ConfigService.normalizeServerApiPrefix(path);
                if (path.equals(currentPrefix) && !currentMappings.isEmpty()) {
                    return;
                }
                unregisterCurrent();
                registerAll(path);
                currentPrefix = path;
                logger.info("Node API routes active under prefix {}", path);
            } catch (Exception e) {
                logger.error("Failed to refresh node API routes", e);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        synchronized (lock) {
            unregisterCurrent();
        }
    }

    private void registerAll(String prefix) throws Exception {
        RequestMappingInfo.BuilderConfiguration options = new RequestMappingInfo.BuilderConfiguration();
        if (handlerMapping.getPatternParser() != null) {
            options.setPatternParser(handlerMapping.getPatternParser());
        }
        if (handlerMapping.getPathMatcher() != null) {
            options.setPathMatcher(handlerMapping.getPathMatcher());
        }

        for (int i = 0; i < ACTIONS.length; i++) {
            String action = ACTIONS[i];
            String methodName = METHODS[i];
            String fullPath = prefix + "/" + action;
            Method method = resolveMethod(methodName);
            RequestMethod httpMethod = ("push".equals(methodName) || "alive".equals(methodName))
                    ? RequestMethod.POST
                    : RequestMethod.GET;
            RequestMappingInfo mappingInfo = RequestMappingInfo
                    .paths(fullPath)
                    .methods(httpMethod)
                    .options(options)
                    .build();
            handlerMapping.registerMapping(mappingInfo, uniProxyController, method);
            currentMappings.add(mappingInfo);
        }
    }

    private Method resolveMethod(String methodName) throws NoSuchMethodException {
        return switch (methodName) {
            case "config", "user", "alivelist" -> UniProxyController.class.getMethod(methodName, HttpServletRequest.class);
            case "push", "alive" -> UniProxyController.class.getMethod(methodName, HttpServletRequest.class, Map.class);
            default -> throw new NoSuchMethodException(methodName);
        };
    }

    private void unregisterCurrent() {
        for (RequestMappingInfo mapping : currentMappings) {
            try {
                handlerMapping.unregisterMapping(mapping);
            } catch (Exception e) {
                logger.warn("Failed to unregister node route: {}", e.getMessage());
            }
        }
        currentMappings.clear();
        currentPrefix = null;
    }
}
