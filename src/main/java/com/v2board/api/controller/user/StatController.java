package com.v2board.api.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.model.StatUser;
import com.v2board.api.model.User;
import com.v2board.api.mapper.StatUserMapper;
import com.v2board.api.mapper.PlanMapper;
import com.v2board.api.model.Plan;
import com.v2board.api.service.CacheService;
import com.v2board.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
public class StatController {

    @Autowired
    private UserService userService;

    @Autowired
    private StatUserMapper statUserMapper;

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private CacheService cacheService;

    @org.springframework.beans.factory.annotation.Value("${v2board.allow-new-period:0}")
    private Integer allowNewPeriod;

    @org.springframework.beans.factory.annotation.Value("${v2board.show-subscribe-method:0}")
    private Integer subscribeMethod;

    @org.springframework.beans.factory.annotation.Value("${v2board.subscribe-path:/api/v1/client/subscribe}")
    private String subscribePath;

    @org.springframework.beans.factory.annotation.Value("${v2board.subscribe-url:}")
    private String subscribeUrlConfig;

    @org.springframework.beans.factory.annotation.Value("${v2board.show-subscribe-expire:5}")
    private Integer subscribeExpire;

    /**
     * 订阅相关统计信息（兼容 PHP UserController::getSubscribe 返回结构）。
     */
    @GetMapping("/getSubscribe")
    public ApiResponse<Map<String, Object>> getSubscribe(HttpServletRequest request) {
        Object attr = request.getAttribute("user");
        if (!(attr instanceof User user)) {
            return ApiResponse.success();
        }
        Map<String, Object> data = new HashMap<>();
        data.put("plan_id", user.getPlanId());
        data.put("token", user.getToken());
        data.put("expired_at", user.getExpiredAt());
        data.put("u", user.getU());
        data.put("d", user.getD());
        data.put("transfer_enable", user.getTransferEnable());
        data.put("device_limit", user.getDeviceLimit());
        data.put("email", user.getEmail());
        data.put("uuid", user.getUuid());

        if (user.getPlanId() != null) {
            Plan plan = planMapper.selectById(user.getPlanId());
            if (plan != null) {
                data.put("plan", plan);
            }
        }

        // 在线设备数
        Integer aliveIp = cacheService.getAliveIpCount(user.getId());
        data.put("alive_ip", aliveIp != null ? aliveIp : 0);

        // 订阅链接
        String subscribeUrl = com.v2board.api.util.Helper.getSubscribeUrl(
                user.getToken(),
                user.getId(),
                subscribeMethod,
                subscribePath,
                subscribeUrlConfig,
                subscribeExpire
        );
        data.put("subscribe_url", subscribeUrl);

        data.put("reset_day", userService.getResetDay(user));
        data.put("allow_new_period", allowNewPeriod != null ? allowNewPeriod : 0);

        return ApiResponse.success(data);
    }

    /**
     * 流量日志，对齐 PHP V1\\User\\StatController::getTrafficLog。
     * 兼容路径：
     * - /api/v1/user/stat/getTrafficLog
     * - /api/v1/user/trafficLog
     */
    @GetMapping({"/trafficLog", "/stat/getTrafficLog"})
    public ApiResponse<List<StatUser>> getTrafficLog(HttpServletRequest request) {
        Object attr = request.getAttribute("user");
        if (!(attr instanceof User user)) {
            return ApiResponse.success(List.of());
        }
        // 月初 00:00 的时间戳（Unix 秒）
        LocalDate firstDayOfMonth = LocalDate.now().withDayOfMonth(1);
        long from = firstDayOfMonth.atStartOfDay().toEpochSecond(ZoneOffset.UTC);

        LambdaQueryWrapper<StatUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StatUser::getUserId, user.getId())
                .ge(StatUser::getRecordAt, from)
                .orderByDesc(StatUser::getRecordAt);

        List<StatUser> list = statUserMapper.selectList(wrapper);
        return ApiResponse.success(list);
    }
}

