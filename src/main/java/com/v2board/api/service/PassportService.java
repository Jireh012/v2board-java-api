package com.v2board.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.InviteCodeMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.InviteCode;
import com.v2board.api.model.Plan;
import com.v2board.api.model.User;
import com.v2board.api.util.CacheKeyUtil;
import com.v2board.api.util.Helper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 对齐 PHP Passport AuthController / CommController 业务逻辑
 */
@Service
public class PassportService {

    @Autowired
    private ConfigService configService;
    @Autowired
    private NodeCacheService nodeCacheService;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private InviteCodeMapper inviteCodeMapper;
    @Autowired
    private com.v2board.api.mapper.PlanMapper planMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private AuthService authService;
    @Autowired
    private MailService mailService;

    public Map<String, Object> register(Map<String, Object> body, HttpServletRequest request) throws Exception {
        String email = str(body.get("email"));
        String password = str(body.get("password"));
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            throw new BusinessException(422, "邮箱和密码不能为空");
        }

        Map<String, Object> full = configService.getFullConfig();
        Map<String, Object> site = mapSection(full, "site");
        Map<String, Object> safe = mapSection(full, "safe");
        Map<String, Object> inviteCfg = mapSection(full, "invite");

        String ip = request != null ? request.getRemoteAddr() : "";
        if (intVal(safe.get("register_limit_by_ip_enable")) == 1) {
            String key = CacheKeyUtil.get("REGISTER_IP_RATE_LIMIT", ip);
            int count = toInt(nodeCacheService.get(key));
            int limit = intVal(safe.get("register_limit_count"), 3);
            if (count >= limit) {
                throw new BusinessException(500, "Register frequently, please try again later");
            }
        }
        if (intVal(site.get("stop_register")) == 1) {
            throw new BusinessException(500, "Registration has closed");
        }
        checkEmailPolicy(email, safe);

        if (intVal(inviteCfg.get("invite_force")) == 1 && !StringUtils.hasText(str(body.get("invite_code")))) {
            throw new BusinessException(500, "You must use the invitation code to register");
        }

        String cacheKeyEmail = email.toLowerCase().trim();
        if (intVal(safe.get("email_verify")) == 1) {
            verifyEmailCode(cacheKeyEmail, str(body.get("email_code")));
        }

        if (userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) != null) {
            throw new BusinessException(500, "Email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(userService.encodePassword(password));
        user.setUuid(Helper.guid(true));
        user.setToken(Helper.guid());
        user.setBanned(0);
        user.setCreatedAt(System.currentTimeMillis() / 1000);
        user.setUpdatedAt(user.getCreatedAt());

        String inviteCodeStr = str(body.get("invite_code"));
        if (StringUtils.hasText(inviteCodeStr)) {
            InviteCode inviteCode = inviteCodeMapper.selectOne(
                    new LambdaQueryWrapper<InviteCode>()
                            .eq(InviteCode::getCode, inviteCodeStr)
                            .eq(InviteCode::getStatus, 0));
            if (inviteCode == null) {
                if (intVal(inviteCfg.get("invite_force")) == 1) {
                    throw new BusinessException(500, "Invalid invitation code");
                }
            } else {
                user.setInviteUserId(inviteCode.getUserId());
                if (intVal(inviteCfg.get("invite_never_expire")) != 1) {
                    inviteCode.setStatus(1);
                    inviteCode.setUpdatedAt(System.currentTimeMillis() / 1000);
                    inviteCodeMapper.updateById(inviteCode);
                }
            }
        }

        int tryOutPlanId = intVal(site.get("try_out_plan_id"));
        if (tryOutPlanId > 0) {
            Plan plan = planMapper.selectById((long) tryOutPlanId);
            if (plan != null) {
                user.setTransferEnable(plan.getTransferEnable() != null ? plan.getTransferEnable() * 1073741824L : 0L);
                user.setDeviceLimit(plan.getDeviceLimit());
                user.setPlanId(plan.getId());
                user.setGroupId(plan.getGroupId());
                int tryOutHour = intVal(site.get("try_out_hour"), 1);
                user.setExpiredAt(System.currentTimeMillis() / 1000 + tryOutHour * 3600L);
                user.setSpeedLimit(plan.getSpeedLimit());
            }
        }

        if (userMapper.insert(user) <= 0) {
            throw new BusinessException(500, "Register failed");
        }

        if (intVal(safe.get("email_verify")) == 1) {
            nodeCacheService.delete(CacheKeyUtil.get("EMAIL_VERIFY_CODE", cacheKeyEmail));
        }
        user.setLastLoginAt(System.currentTimeMillis() / 1000);
        userMapper.updateById(user);

        if (intVal(safe.get("register_limit_by_ip_enable")) == 1) {
            String key = CacheKeyUtil.get("REGISTER_IP_RATE_LIMIT", ip);
            int count = toInt(nodeCacheService.get(key));
            int expireMin = intVal(safe.get("register_limit_expire"), 60);
            nodeCacheService.set(key, count + 1, Duration.ofMinutes(expireMin));
        }

        Map<String, Object> data = authService.generateAuthData(user, request);
        if (data == null) {
            throw new BusinessException(500, "Register failed");
        }
        return data;
    }

    public boolean forget(Map<String, Object> body) throws Exception {
        String email = str(body.get("email"));
        String inputCode = str(body.get("email_code"));
        String password = str(body.get("password"));
        if (!StringUtils.hasText(email) || !StringUtils.hasText(inputCode) || !StringUtils.hasText(password)) {
            throw new BusinessException(500, "Incorrect email verification code");
        }
        if (!inputCode.matches("\\d{6}")) {
            throw new BusinessException(500, "Incorrect email verification code");
        }

        String cacheKeyEmail = email.toLowerCase().trim();
        String forgetLimitKey = CacheKeyUtil.get("FORGET_REQUEST_LIMIT", cacheKeyEmail);
        int forgetLimit = toInt(nodeCacheService.get(forgetLimitKey));
        if (forgetLimit >= 3) {
            throw new BusinessException(500, "Reset failed, Please try again later");
        }

        Object cached = nodeCacheService.get(CacheKeyUtil.get("EMAIL_VERIFY_CODE", cacheKeyEmail));
        if (!codeEquals(cached, inputCode)) {
            nodeCacheService.set(forgetLimitKey, forgetLimit + 1, Duration.ofSeconds(300));
            throw new BusinessException(500, "Incorrect email verification code");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            throw new BusinessException(500, "This email is not registered in the system");
        }
        userService.updatePassword(user, password);
        nodeCacheService.delete(CacheKeyUtil.get("EMAIL_VERIFY_CODE", cacheKeyEmail));
        authService.removeAllSession(user);
        return true;
    }

    public boolean sendEmailVerify(Map<String, Object> body, String clientIp) throws Exception {
        String ip = clientIp != null ? clientIp : "";
        String rateKey = "SEND_EMAIL_IP_RATE_" + ip;
        Object rateObj = cacheService.get(rateKey);
        int rateCount = rateObj instanceof Number n ? n.intValue() : 0;
        if (rateCount >= 3) {
            throw new BusinessException(429, "Too many requests, please try again later.");
        }
        cacheService.set(rateKey, rateCount + 1, 60, java.util.concurrent.TimeUnit.SECONDS);

        String email = str(body.get("email"));
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(422, "邮箱不能为空");
        }

        Map<String, Object> full = configService.getFullConfig();
        Map<String, Object> safe = mapSection(full, "safe");
        checkEmailPolicy(email, safe);

        Object isForgetObj = body.get("isforget");
        if (isForgetObj != null) {
            int isForget = isForgetObj instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(isForgetObj));
            boolean exists = userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
            if (isForget == 0 && exists) {
                throw new BusinessException(500, "This email is registered");
            }
            if (isForget == 1 && !exists) {
                throw new BusinessException(500, "This email is not registered in the system");
            }
        }

        String cacheKeyEmail = email.toLowerCase().trim();
        if (nodeCacheService.get(CacheKeyUtil.get("LAST_SEND_EMAIL_VERIFY_TIMESTAMP", cacheKeyEmail)) != null) {
            throw new BusinessException(500, "Email verification code has been sent, please request again later");
        }

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 1000000));
        Map<String, Object> site = mapSection(full, "site");
        String appName = str(site.get("app_name"));
        if (!StringUtils.hasText(appName)) {
            appName = "V2Board";
        }
        String appUrl = str(site.get("app_url"));

        mailService.sendEmail(email, appName + " Email verification code", "verify",
                Map.of("name", appName, "code", code, "url", appUrl != null ? appUrl : ""));

        nodeCacheService.set(CacheKeyUtil.get("EMAIL_VERIFY_CODE", cacheKeyEmail), code, Duration.ofSeconds(300));
        nodeCacheService.set(CacheKeyUtil.get("LAST_SEND_EMAIL_VERIFY_TIMESTAMP", cacheKeyEmail),
                System.currentTimeMillis() / 1000, Duration.ofSeconds(60));
        return true;
    }

    public String getQuickLoginUrl(String authData, String redirect) throws Exception {
        Map<String, Object> userMap = authService.decryptAuthData(authData);
        if (userMap == null || userMap.get("id") == null) {
            throw new BusinessException(403, "未登录或登陆已过期");
        }
        Long userId = ((Number) userMap.get("id")).longValue();
        String code = Helper.guid();
        nodeCacheService.set(CacheKeyUtil.get("TEMP_TOKEN", code), userId, Duration.ofSeconds(60));

        String path = "/#/login?verify=" + code + "&redirect=" + (StringUtils.hasText(redirect) ? redirect : "dashboard");
        Map<String, Object> full = configService.getFullConfig();
        Map<String, Object> site = mapSection(full, "site");
        String appUrl = str(site.get("app_url"));
        if (StringUtils.hasText(appUrl)) {
            return appUrl + path;
        }
        return path;
    }

    public Map<String, Object> verifyLogin(String verifyCode, HttpServletRequest request) {
        if (!StringUtils.hasText(verifyCode)) {
            throw new BusinessException(500, "Token error");
        }
        String key = CacheKeyUtil.get("TEMP_TOKEN", verifyCode);
        Object userIdObj = nodeCacheService.get(key);
        if (userIdObj == null) {
            throw new BusinessException(500, "Token error");
        }
        Long userId = userIdObj instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(userIdObj));
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(500, "The user does not exist");
        }
        if (user.getBanned() != null && user.getBanned() == 1) {
            throw new BusinessException(500, "Your account has been suspended");
        }
        nodeCacheService.delete(key);
        Map<String, Object> data = authService.generateAuthData(user, request);
        if (data == null) {
            throw new BusinessException(500, "Login failed");
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapSection(Map<String, Object> full, String key) {
        Object v = full.get(key);
        if (v instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private void checkEmailPolicy(String email, Map<String, Object> safe) {
        if (intVal(safe.get("email_whitelist_enable")) == 1) {
            Object suffix = safe.get("email_whitelist_suffix");
            if (!Helper.emailSuffixVerify(email, suffix != null ? suffix : "")) {
                throw new BusinessException(500, "Email suffix is not in the Whitelist");
            }
        }
        if (intVal(safe.get("email_gmail_limit_enable")) == 1) {
            String prefix = email.split("@")[0];
            if (prefix.contains(".") || prefix.contains("+")) {
                throw new BusinessException(500, "Gmail alias is not supported");
            }
        }
    }

    private void verifyEmailCode(String cacheKeyEmail, String inputCode) {
        if (!StringUtils.hasText(inputCode) || !inputCode.matches("\\d{6}")) {
            throw new BusinessException(500, "Incorrect email verification code");
        }
        Object cached = nodeCacheService.get(CacheKeyUtil.get("EMAIL_VERIFY_CODE", cacheKeyEmail));
        if (!codeEquals(cached, inputCode)) {
            throw new BusinessException(500, "Incorrect email verification code");
        }
    }

    private static boolean codeEquals(Object cached, String inputCode) {
        if (cached == null) {
            return false;
        }
        return MessageDigest.isEqual(
                String.valueOf(cached).getBytes(StandardCharsets.UTF_8),
                inputCode.getBytes(StandardCharsets.UTF_8));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static int intVal(Object o) {
        return intVal(o, 0);
    }

    private static int intVal(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o));
            } catch (NumberFormatException ignore) {
            }
        }
        return def;
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o));
            } catch (NumberFormatException ignore) {
            }
        }
        return 0;
    }
}
