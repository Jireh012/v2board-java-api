package com.v2board.api.controller.user;

import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.model.User;
import com.v2board.api.service.ServerService;
import com.v2board.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/user/server")
public class ServerController {

    @Autowired
    private ServerService serverService;

    @Autowired
    private UserService userService;

    /**
     * 对齐 PHP V1\\User\\ServerController::fetch
     */
    @GetMapping("/fetch")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> fetch(HttpServletRequest request,
                                                                        HttpServletResponse response) {
        User user = requireUser(request);
        List<Map<String, Object>> servers = new ArrayList<>();
        if (userService.isAvailable(user)) {
            servers = serverService.getAvailableServers(user);
        }
        String eTag = generateETag(servers);
        String ifNoneMatch = request.getHeader("If-None-Match");
        if (StringUtils.hasText(ifNoneMatch) && ifNoneMatch.contains(eTag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header("ETag", "\"" + eTag + "\"")
                    .body(null);
        }
        return ResponseEntity.ok()
                .header("ETag", "\"" + eTag + "\"")
                .body(ApiResponse.success(servers));
    }

    private User requireUser(HttpServletRequest request) {
        Object attr = request.getAttribute("user");
        if (attr instanceof User user) {
            return user;
        }
        throw new BusinessException(401, "Unauthenticated");
    }

    private String generateETag(List<Map<String, Object>> servers) {
        try {
            List<Object> cacheKeys = servers.stream()
                    .map(s -> s.get("cache_key"))
                    .collect(Collectors.toList());
            String json = cacheKeys.toString();
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}

