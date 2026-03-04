package com.v2board.api.controller.user;

import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.GiftcardMapper;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.TicketMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Giftcard;
import com.v2board.api.model.Order;
import com.v2board.api.model.Plan;
import com.v2board.api.model.Ticket;
import com.v2board.api.model.User;
import com.v2board.api.service.AuthService;
import com.v2board.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
public class UserProfileController {

    @Autowired
    private UserService userService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private GiftcardMapper giftcardMapper;

    @Autowired
    private AuthService authService;

    @Value("${v2board.app-url:}")
    private String appUrl;

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info(HttpServletRequest request) {
        User current = requireUser(request);
        Map<String, Object> data = new HashMap<>();
        data.put("email", current.getEmail());
        data.put("transfer_enable", current.getTransferEnable());
        data.put("device_limit", current.getDeviceLimit());
        data.put("last_login_at", current.getLastLoginAt());
        data.put("created_at", current.getCreatedAt());
        data.put("banned", current.getBanned());
        data.put("auto_renewal", current.getAutoRenewal());
        data.put("remind_expire", current.getRemindExpire());
        data.put("remind_traffic", current.getRemindTraffic());
        data.put("expired_at", current.getExpiredAt());
        data.put("balance", current.getBalance());
        data.put("commission_balance", current.getCommissionBalance());
        data.put("plan_id", current.getPlanId());
        data.put("discount", current.getDiscount());
        data.put("commission_rate", current.getCommissionRate());
        data.put("telegram_id", current.getTelegramId());
        data.put("uuid", current.getUuid());
        return ApiResponse.success(data);
    }

    @PostMapping("/changePassword")
    public ApiResponse<Boolean> changePassword(HttpServletRequest request,
                                               @RequestParam("old_password") String old_password,
                                               @RequestParam("new_password") String new_password) {
        User current = requireUser(request);
        if (!userService.verifyPassword(current, old_password)) {
            throw new BusinessException(500, "The old password is wrong");
        }
        userService.updatePassword(current, new_password);
        // 密码修改后清空所有会话
        authService.removeAllSession(current);
        return ApiResponse.success(true);
    }

    /**
     * 获取当前用户活跃会话列表，对齐 PHP UserController::getActiveSession。
     */
    @GetMapping("/getActiveSession")
    public ApiResponse<Map<String, Object>> getActiveSession(HttpServletRequest request) {
        User current = requireUser(request);
        Map<String, Object> sessions = authService.getSessions(current.getId());
        return ApiResponse.success(sessions);
    }

    /**
     * 移除指定会话，对齐 PHP UserController::removeActiveSession。
     */
    @PostMapping("/removeActiveSession")
    public ApiResponse<Boolean> removeActiveSession(HttpServletRequest request,
                                                    @RequestParam("session_id") String sessionId) {
        User current = requireUser(request);
        boolean ok = authService.removeSession(current.getId(), sessionId);
        return ApiResponse.success(ok);
    }

    /**
     * 用户状态统计，对齐 PHP UserController::getStat。
     */
    @GetMapping("/getStat")
    public ApiResponse<int[]> getStat(HttpServletRequest request) {
        User current = requireUser(request);
        Long userId = current.getId();
        long pendingOrdersLong = orderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .eq(Order::getStatus, 0)
        );
        long pendingTicketsLong = ticketMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getUserId, userId)
                        .eq(Ticket::getStatus, 0)
        );
        long inviteCountLong = userMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getInviteUserId, userId)
        );
        int pendingOrders = (int) Math.max(0, Math.min(pendingOrdersLong, Integer.MAX_VALUE));
        int pendingTickets = (int) Math.max(0, Math.min(pendingTicketsLong, Integer.MAX_VALUE));
        int inviteCount = (int) Math.max(0, Math.min(inviteCountLong, Integer.MAX_VALUE));
        return ApiResponse.success(new int[]{pendingOrders, pendingTickets, inviteCount});
    }

    /**
     * 更新个人偏好字段，对齐 PHP UserController::update。
     */
    @PostMapping("/update")
    public ApiResponse<Boolean> update(HttpServletRequest request,
                                       @RequestParam(value = "auto_renewal", required = false) Integer autoRenewal,
                                       @RequestParam(value = "remind_expire", required = false) Integer remindExpire,
                                       @RequestParam(value = "remind_traffic", required = false) Integer remindTraffic) {
        User current = requireUser(request);
        if (autoRenewal != null) {
            current.setAutoRenewal(autoRenewal);
        }
        if (remindExpire != null) {
            current.setRemindExpire(remindExpire);
        }
        if (remindTraffic != null) {
            current.setRemindTraffic(remindTraffic);
        }
        userMapper.updateById(current);
        return ApiResponse.success(true);
    }

    /**
     * 解绑 Telegram，对齐 PHP UserController::unbindTelegram。
     */
    @PostMapping("/unbindTelegram")
    public ApiResponse<Boolean> unbindTelegram(HttpServletRequest request) {
        User current = requireUser(request);
        current.setTelegramId(null);
        userMapper.updateById(current);
        return ApiResponse.success(true);
    }

    /**
     * 重置安全信息（uuid/token），返回新的订阅链接，对齐 PHP UserController::resetSecurity。
     */
    @PostMapping("/resetSecurity")
    public ApiResponse<String> resetSecurity(HttpServletRequest request) {
        User current = requireUser(request);
        current.setUuid(java.util.UUID.randomUUID().toString());
        current.setToken(java.util.UUID.randomUUID().toString().replace("-", ""));
        userMapper.updateById(current);
        String subscribeUrl = com.v2board.api.util.Helper.getSubscribeUrl(
                current.getToken(),
                current.getId(),
                0,
                "/api/v1/client/subscribe",
                "",
                5
        );
        return ApiResponse.success(subscribeUrl);
    }

    /**
     * 兑换礼品卡，对齐 PHP UserController::redeemgiftcard（简化版）。
     */
    @PostMapping("/redeemGiftcard")
    public ApiResponse<Map<String, Object>> redeemGiftcard(HttpServletRequest request,
                                                           @RequestParam("giftcard") String giftcardCode) {
        User user = requireUser(request);
        long now = System.currentTimeMillis() / 1000;
        Giftcard giftcard = giftcardMapper.selectOne(
                new LambdaQueryWrapper<Giftcard>().eq(Giftcard::getCode, giftcardCode)
        );
        if (giftcard == null) {
            throw new BusinessException(500, "The gift card does not exist");
        }
        if (giftcard.getStartedAt() != null && now < giftcard.getStartedAt()) {
            throw new BusinessException(500, "The gift card is not yet valid");
        }
        if (giftcard.getEndedAt() != null && now > giftcard.getEndedAt()) {
            throw new BusinessException(500, "The gift card has expired");
        }
        Integer limitUse = giftcard.getLimitUse();
        if (limitUse != null && limitUse <= 0) {
            throw new BusinessException(500, "The gift card usage limit has been reached");
        }
        String usedIdsStr = giftcard.getUsedUserIds();
        java.util.Set<Long> usedIds = new java.util.HashSet<>();
        if (usedIdsStr != null && !usedIdsStr.isEmpty()) {
            for (String s : usedIdsStr.replace("[", "").replace("]", "").split(",")) {
                s = s.trim();
                if (!s.isEmpty()) {
                    try {
                        usedIds.add(Long.parseLong(s));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        if (usedIds.contains(user.getId())) {
            throw new BusinessException(500, "The gift card has already been used by this user");
        }
        usedIds.add(user.getId());
        giftcard.setUsedUserIds(usedIds.toString());

        switch (giftcard.getType()) {
            case 1: // 余额
                long balance = user.getBalance() != null ? user.getBalance() : 0L;
                user.setBalance(balance + giftcard.getValue());
                break;
            case 2: // 延长有效期（天）
                if (user.getExpiredAt() != null) {
                    long base = user.getExpiredAt() <= now ? now : user.getExpiredAt();
                    user.setExpiredAt(base + giftcard.getValue() * 86400L);
                } else {
                    throw new BusinessException(500, "Not suitable gift card type");
                }
                break;
            case 3: // 增加流量（GB）
                long transferEnable = user.getTransferEnable() != null ? user.getTransferEnable() : 0L;
                user.setTransferEnable(transferEnable + giftcard.getValue() * 1073741824L);
                break;
            case 4: // 清空流量
                user.setU(0L);
                user.setD(0L);
                break;
            case 5: // 指定套餐
                if (user.getPlanId() == null || (user.getExpiredAt() != null && user.getExpiredAt() < now)) {
                    Plan plan = null;
                    if (giftcard.getPlanId() != null) {
                        plan = new Plan();
                        plan.setId(giftcard.getPlanId());
                    }
                    user.setPlanId(giftcard.getPlanId());
                    if (plan != null) {
                        user.setGroupId(plan.getGroupId());
                        if (plan.getTransferEnable() != null) {
                            user.setTransferEnable(plan.getTransferEnable() * 1073741824L);
                        }
                    }
                    user.setU(0L);
                    user.setD(0L);
                    if (giftcard.getValue() == 0) {
                        user.setExpiredAt(null);
                    } else {
                        user.setExpiredAt(now + giftcard.getValue() * 86400L);
                    }
                } else {
                    throw new BusinessException(500, "Not suitable gift card type");
                }
                break;
            default:
                throw new BusinessException(500, "Unknown gift card type");
        }

        if (limitUse != null) {
            giftcard.setLimitUse(limitUse - 1);
        }

        userMapper.updateById(user);
        giftcardMapper.updateById(giftcard);

        Map<String, Object> resp = new HashMap<>();
        resp.put("type", giftcard.getType());
        resp.put("value", giftcard.getValue());
        return ApiResponse.success(resp);
    }

    /**
     * 佣金转余额，对齐 PHP UserController::transfer（简化版）。
     */
    @PostMapping("/transferCommission")
    public ApiResponse<Boolean> transferCommission(HttpServletRequest request,
                                                   @RequestParam("transfer_amount") Long transferAmount) {
        User current = requireUser(request);
        long commission = current.getCommissionBalance() != null ? current.getCommissionBalance() : 0L;
        if (transferAmount == null || transferAmount <= 0 || transferAmount > commission) {
            throw new BusinessException(500, "Insufficient commission balance");
        }
        long newCommission = commission - transferAmount;
        long balance = current.getBalance() != null ? current.getBalance() : 0L;
        long newBalance = balance + transferAmount;

        long now = System.currentTimeMillis() / 1000;
        Order order = new Order();
        order.setUserId(current.getId());
        order.setPlanId(0L);
        order.setPeriod("deposit");
        // 与原版一致，使用 Helper.generateOrderNo() 生成充值订单号
        order.setTradeNo(com.v2board.api.util.Helper.generateOrderNo());
        order.setTotalAmount(0L);
        order.setStatus(3);
        order.setSurplusAmount(transferAmount);
        order.setCallbackNo("Commission transfer");
        order.setType(9); // 充值 / 佣金转余额
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        current.setCommissionBalance(newCommission);
        current.setBalance(newBalance);

        orderMapper.insert(order);
        userMapper.updateById(current);

        return ApiResponse.success(true);
    }

    /**
     * 快速登录 URL，对齐 PHP UserController::getQuickLoginUrl（简化版）。
     */
    @GetMapping("/getQuickLoginUrl")
    public ApiResponse<String> getQuickLoginUrl(HttpServletRequest request,
                                                @RequestParam(value = "redirect", required = false, defaultValue = "dashboard") String redirect) {
        String code = java.util.UUID.randomUUID().toString().replace("-", "");

        String base = appUrl != null && !appUrl.isEmpty() ? appUrl : "";
        String url = base + "/#/login?verify=" + code + "&redirect=" + redirect;
        return ApiResponse.success(url);
    }

    private User requireUser(HttpServletRequest request) {
        Object attr = request.getAttribute("user");
        if (attr instanceof User user) {
            return user;
        }
        throw new BusinessException(401, "Unauthenticated");
    }
}

