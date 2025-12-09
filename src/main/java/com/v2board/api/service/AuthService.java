package com.v2board.api.service;

import com.v2board.api.model.User;
import com.v2board.api.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private CacheService cacheService;
    
    @Value("${app.key:base64:your-secret-key-here}")
    private String appKey;
    
    /**
     * 解密JWT token获取用户信息
     * PHP: AuthService::decryptAuthData($jwt)
     * 
     * @param jwt JWT token
     * @return 用户信息Map，包含id, email, is_admin, is_staff等，如果解密失败返回null
     */
    public Map<String, Object> decryptAuthData(String jwt) {
        if (jwt == null || jwt.isEmpty()) {
            return null;
        }
        
        try {
            // 先检查缓存
            Object cached = cacheService.get(jwt);
            if (cached != null && cached instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cachedUser = (Map<String, Object>) cached;
                return cachedUser;
            }
            
            // 解密JWT
            SecretKey key = Keys.hmacShaKeyFor(getSecretKeyBytes());
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody();
            
            Long userId = claims.get("id", Long.class);
            String session = claims.get("session", String.class);
            
            if (userId == null || session == null) {
                return null;
            }
            
            // 检查session是否有效
            if (!checkSession(userId, session)) {
                return null;
            }
            
            // 查询用户信息
            User user = userMapper.selectById(userId);
            if (user == null) {
                return null;
            }
            
            // 构建用户信息Map
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId());
            userMap.put("email", user.getEmail());
            // 注意：User模型中可能没有is_admin和is_staff字段，需要根据实际情况调整
            userMap.put("is_admin", false);  // 默认值，需要根据实际数据库字段调整
            userMap.put("is_staff", false);  // 默认值，需要根据实际数据库字段调整
            
            // 缓存1小时
            cacheService.set(jwt, userMap, 3600, java.util.concurrent.TimeUnit.SECONDS);
            
            return userMap;
        } catch (Exception e) {
            logger.error("Error decrypting auth data", e);
            return null;
        }
    }
    
    /**
     * 检查session是否有效
     * PHP: checkSession($userId, $session)
     */
    private boolean checkSession(Long userId, String session) {
        try {
            String cacheKey = "USER_SESSIONS_" + userId;
            @SuppressWarnings("unchecked")
            Map<String, Object> sessions = (Map<String, Object>) cacheService.get(cacheKey);
            if (sessions == null) {
                return false;
            }
            return sessions.containsKey(session);
        } catch (Exception e) {
            logger.error("Error checking session for user {}", userId, e);
            return false;
        }
    }
    
    /**
     * 获取密钥字节数组
     * 支持base64:前缀的配置格式
     */
    private byte[] getSecretKeyBytes() {
        if (appKey.startsWith("base64:")) {
            String base64Key = appKey.substring(7);
            return java.util.Base64.getDecoder().decode(base64Key);
        }
        return appKey.getBytes(StandardCharsets.UTF_8);
    }
}

