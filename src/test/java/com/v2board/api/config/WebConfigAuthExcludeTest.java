package com.v2board.api.config;

import com.v2board.api.middleware.ClientAuthInterceptor;
import com.v2board.api.middleware.ClientTokenInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WebConfigAuthExcludeTest {

    @Test
    @SuppressWarnings("unchecked")
    void clientAuthInterceptor_excludesAdminLogin() {
        WebConfig config = new WebConfig();
        ReflectionTestUtils.setField(config, "clientTokenInterceptor", mock(ClientTokenInterceptor.class));
        ReflectionTestUtils.setField(config, "clientAuthInterceptor", mock(ClientAuthInterceptor.class));

        InterceptorRegistry registry = new InterceptorRegistry();
        config.addInterceptors(registry);

        List<InterceptorRegistration> registrations =
                (List<InterceptorRegistration>) ReflectionTestUtils.getField(registry, "registrations");
        boolean found = false;
        for (InterceptorRegistration registration : registrations) {
            List<String> excludes =
                    (List<String>) ReflectionTestUtils.getField(registration, "excludePatterns");
            if (excludes != null && excludes.contains("/api/v1/admin/login")) {
                found = true;
                break;
            }
        }
        assertTrue(found, "ClientAuthInterceptor must exclude /api/v1/admin/login");
    }
}
