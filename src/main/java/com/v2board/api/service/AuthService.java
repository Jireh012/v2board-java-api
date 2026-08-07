package com.v2board.api.service;

import com.v2board.api.mapper.UserLoginLogMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.User;
import com.v2board.api.model.UserLoginLog;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserLoginLogMapper userLoginLogMapper;
    
    @Autowired
    private CacheService cacheService;
    
    @Value("${app.key:base64:your-secret-key-here}")
    private String appKey;
    
    /**
     * 登录成功后生成 auth_data，与 PHP AuthService::generateAuthData 对齐：
     * - 生成 session GUID
     * - 使用 app.key(HS256) 签发 JWT，claims 含 id 与 session
     * - 在缓存中记录 USER_SESSIONS_<userId>，保存本次会话元信息
     * - 返回包含 token / is_admin / auth_data 的 Map
     */
    public Map<String, Object> generateAuthData(User user, HttpServletRequest request) {
        if (user == null || user.getId() == null) {
            return null;
        }
        // 会话 ID
        String session = UUID.randomUUID().toString().replace("-", "");

        // 构造 JWT
        SecretKey key = Keys.hmacShaKeyFor(getSecretKeyBytes());
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("session", session);

        Date now = new Date();
        // 为了兼容 PHP 行为，这里不强制设置过期时间，由服务端 Session 控制有效性
        String jwt = Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        // 写入会话缓存，key 形如 USER_SESSIONS_1
        addSession(user.getId(), session, request, jwt);
        recordLogin(user, request);

        Map<String, Object> result = new HashMap<>();
        result.put("token", user.getToken());
        // 根据 v2_user.is_admin 字段判断是否为管理员
        boolean isAdmin = user.getIsAdmin() != null && user.getIsAdmin() == 1;
        result.put("is_admin", isAdmin);
        result.put("auth_data", jwt);
        return result;
    }

    /**
     * 持久化登录记录 + 更新 last_login_at。失败不影响登录本身。
     */
    private void recordLogin(User user, HttpServletRequest request) {
        long now = System.currentTimeMillis() / 1000;
        try {
            User patch = new User();
            patch.setId(user.getId());
            patch.setLastLoginAt(now);
            patch.setUpdatedAt(now);
            userMapper.updateById(patch);
            user.setLastLoginAt(now);
        } catch (Exception e) {
            logger.warn("Failed to update last_login_at for user {}", user.getId(), e);
        }
        try {
            UserLoginLog log = new UserLoginLog();
            log.setUserId(user.getId());
            log.setIp(resolveClientIp(request));
            log.setUserAgent(truncate(request != null ? request.getHeader("User-Agent") : null, 512));
            log.setCreatedAt(now);
            userLoginLogMapper.insert(log);
        } catch (Exception e) {
            logger.warn("Failed to insert login log for user {} (table v2_user_login_log may be missing)",
                    user.getId(), e);
        }
    }

    private static String resolveClientIp(HttpServletRequest request) {
        if (request == null) return "";
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            String first = xff.split(",")[0].trim();
            if (StringUtils.hasText(first)) return truncate(first, 64);
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) return truncate(realIp.trim(), 64);
        String remote = request.getRemoteAddr();
        return remote != null ? truncate(remote, 64) : "";
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
    
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
            boolean isAdmin = user.getIsAdmin() != null && user.getIsAdmin() == 1;
            userMap.put("is_admin", isAdmin);
            userMap.put("is_staff", false);  // 目前暂不区分 staff，保持为 false
            
            // 缓存1小时
            cacheService.set(jwt, userMap, 3600, java.util.concurrent.TimeUnit.SECONDS);
            
            return userMap;
        } catch (Exception e) {
            logger.error("Error decrypting auth data", e);
            return null;
        }
    }
    
    /**
     * 新增会话信息，兼容 PHP USER_SESSIONS 结构：
     * sessions[sessionId] = {ip, login_at, ua, auth_data}
     */
    @SuppressWarnings("unchecked")
    private void addSession(Long userId, String sessionId, HttpServletRequest request, String authData) {
        try {
            String cacheKey = "USER_SESSIONS_" + userId;
            Object existing = cacheService.get(cacheKey);
            Map<String, Object> sessions;
            if (existing instanceof Map) {
                sessions = (Map<String, Object>) existing;
            } else {
                sessions = new HashMap<>();
            }
            Map<String, Object> meta = new HashMap<>();
            String ip = request != null ? request.getRemoteAddr() : "";
            String ua = request != null ? request.getHeader("User-Agent") : "";
            meta.put("ip", ip);
            meta.put("login_at", System.currentTimeMillis() / 1000);
            meta.put("ua", ua);
            meta.put("auth_data", authData);
            sessions.put(sessionId, meta);
            // 这里设置一个较长的过期时间（例如 30 天），接近 PHP 默认行为
            cacheService.set(cacheKey, sessions, 30L * 24 * 3600, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Error adding session {} for user {}", sessionId, userId, e);
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
     * 获取用户的所有活跃 session 列表（简化为缓存中的 key 集合）。
     * 对齐 PHP AuthService::getSessions 的返回形态（这里只返回 sessionId 列表）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSessions(Long userId) {
        try {
            String cacheKey = "USER_SESSIONS_" + userId;
            Object data = cacheService.get(cacheKey);
            if (data instanceof Map) {
                return (Map<String, Object>) data;
            }
            return new HashMap<>();
        } catch (Exception e) {
            logger.error("Error getting sessions for user {}", userId, e);
            return new HashMap<>();
        }
    }

    /**
     * 移除单个 session。
     */
    @SuppressWarnings("unchecked")
    public boolean removeSession(Long userId, String sessionId) {
        try {
            String cacheKey = "USER_SESSIONS_" + userId;
            Object data = cacheService.get(cacheKey);
            if (!(data instanceof Map)) {
                return false;
            }
            Map<String, Object> sessions = (Map<String, Object>) data;
            Object removed = sessions.remove(sessionId);
            cacheService.set(cacheKey, sessions, 3600, java.util.concurrent.TimeUnit.SECONDS);
            return removed != null;
        } catch (Exception e) {
            logger.error("Error removing session {} for user {}", sessionId, userId, e);
            return false;
        }
    }

    /**
     * 移除用户所有 session。
     */
    public void removeAllSession(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        try {
            String cacheKey = "USER_SESSIONS_" + user.getId();
            cacheService.delete(cacheKey);
        } catch (Exception e) {
            logger.error("Error removing all sessions for user {}", user.getId(), e);
        }
    }
    /**
     * 获取密钥字节数组。
     * 支持 Laravel 格式 {@code base64:xxxx}；合法 APP_KEY 解码后为 32 字节。
     * 若配置为占位符或长度不足 256 bit，则 SHA-256 派生到 32 字节，避免 jjwt WeakKeyException。
     */
    private byte[] getSecretKeyBytes() {
        byte[] keyBytes;
        if (appKey != null && appKey.startsWith("base64:")) {
            String base64Key = appKey.substring(7);
            try {
                keyBytes = java.util.Base64.getDecoder().decode(base64Key);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid base64 app.key, fallback to raw string key: {}", e.getMessage());
                keyBytes = base64Key.getBytes(StandardCharsets.UTF_8);
            }
        } else {
            keyBytes = (appKey != null ? appKey : "").getBytes(StandardCharsets.UTF_8);
        }
        // HS256 要求密钥 >= 256 bit；Laravel 正式 APP_KEY 解码后恰好 32 字节
        if (keyBytes.length < 32) {
            logger.warn("app.key is only {} bits (<256). Deriving SHA-256 key. "
                            + "Set a real Laravel APP_KEY (base64:...) for production / PHP JWT compatibility.",
                    keyBytes.length * 8);
            try {
                return java.security.MessageDigest.getInstance("SHA-256").digest(keyBytes);
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 not available", e);
            }
        }
        return keyBytes;
    }
}

