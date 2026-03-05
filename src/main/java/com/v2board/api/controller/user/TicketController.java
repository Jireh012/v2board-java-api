package com.v2board.api.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.TicketMapper;
import com.v2board.api.mapper.TicketMessageMapper;
import com.v2board.api.model.Order;
import com.v2board.api.model.Ticket;
import com.v2board.api.model.TicketMessage;
import com.v2board.api.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user/ticket")
public class TicketController {

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private TicketMessageMapper ticketMessageMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Value("${v2board.ticket-status:0}")
    private Integer ticketStatus;

    @Value("${v2board.withdraw-close-enable:0}")
    private Integer withdrawCloseEnable;

    @Value("${v2board.commission-withdraw-method:alipay,wechat}")
    private String withdrawMethods;

    @Value("${v2board.commission-withdraw-limit:100}")
    private Integer withdrawLimit;

    /**
     * 获取工单列表或单个工单详情，对齐 PHP User\\TicketController::fetch
     */
    @GetMapping("/fetch")
    public ApiResponse<Object> fetch(HttpServletRequest request,
            @RequestParam(value = "id", required = false) Long id) {
        User user = requireUser(request);
        if (id != null) {
            Ticket ticket = ticketMapper.selectOne(
                    new LambdaQueryWrapper<Ticket>()
                            .eq(Ticket::getId, id)
                            .eq(Ticket::getUserId, user.getId()));
            if (ticket == null) {
                throw new BusinessException(500, "Ticket does not exist");
            }
            List<TicketMessage> messages = ticketMessageMapper.selectList(
                    new LambdaQueryWrapper<TicketMessage>()
                            .eq(TicketMessage::getTicketId, ticket.getId())
                            .orderByAsc(TicketMessage::getId));
            for (TicketMessage m : messages) {
                m.setIsMe(m.getUserId().equals(ticket.getUserId()));
            }
            ticket.setMessage(messages);
            return ApiResponse.success(ticket);
        }
        List<Ticket> list = ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getUserId, user.getId())
                        .orderByDesc(Ticket::getCreatedAt));
        return ApiResponse.success(list);
    }

    /**
     * 创建工单，对齐 PHP User\\TicketController::save（不含 Telegram 通知）。
     */
    @PostMapping("/save")
    public ApiResponse<Boolean> save(HttpServletRequest request,
            @RequestParam("subject") String subject,
            @RequestParam("level") Integer level,
            @RequestParam("message") String message) {
        User user = requireUser(request);
        // 检查是否有未解决工单
        long openCount = ticketMapper.selectCount(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getUserId, user.getId())
                        .eq(Ticket::getStatus, 0));
        if (openCount > 0) {
            throw new BusinessException(500, "There are other unresolved tickets");
        }

        // 工单开单策略
        switch (ticketStatus != null ? ticketStatus : 0) {
            case 0:
                break;
            case 1:
                // 仅允许有付费订单的用户开单
                long hasOrder = orderMapper.selectCount(
                        new LambdaQueryWrapper<Order>()
                                .eq(Order::getUserId, user.getId())
                                .in(Order::getStatus, 3, 4));
                if (hasOrder == 0) {
                    throw new BusinessException(500, "请先购买套餐");
                }
                break;
            case 2:
                throw new BusinessException(500, "当前套餐不允许发起工单");
            default:
                throw new BusinessException(500, "未知的工单状态");
        }

        long now = System.currentTimeMillis() / 1000;
        Ticket ticket = new Ticket();
        ticket.setUserId(user.getId());
        ticket.setSubject(subject);
        ticket.setLevel(level != null ? level : 1);
        ticket.setStatus(0);
        ticket.setReplyStatus(0);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        int inserted = ticketMapper.insert(ticket);
        if (inserted <= 0) {
            throw new BusinessException(500, "Failed to open ticket");
        }
        TicketMessage tm = new TicketMessage();
        tm.setTicketId(ticket.getId());
        tm.setUserId(user.getId());
        tm.setMessage(message);
        tm.setCreatedAt(now);
        tm.setUpdatedAt(now);
        if (ticketMessageMapper.insert(tm) <= 0) {
            throw new BusinessException(500, "Failed to open ticket");
        }
        return ApiResponse.success(true);
    }

    /**
     * 用户回复工单，对齐 PHP User\\TicketController::reply（简化版）。
     */
    @PostMapping("/reply")
    public ApiResponse<Boolean> reply(HttpServletRequest request,
            @RequestParam("id") Long id,
            @RequestParam("message") String message) {
        if (id == null) {
            throw new BusinessException(500, "Invalid parameter");
        }
        if (!StringUtils.hasText(message)) {
            throw new BusinessException(500, "Message cannot be empty");
        }
        User user = requireUser(request);
        Ticket ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getId, id)
                        .eq(Ticket::getUserId, user.getId()));
        if (ticket == null) {
            throw new BusinessException(500, "Ticket does not exist");
        }
        if (ticket.getStatus() != null && ticket.getStatus() != 0) {
            throw new BusinessException(500, "The ticket is closed and cannot be replied");
        }
        TicketMessage last = ticketMessageMapper.selectOne(
                new LambdaQueryWrapper<TicketMessage>()
                        .eq(TicketMessage::getTicketId, ticket.getId())
                        .orderByDesc(TicketMessage::getId)
                        .last("LIMIT 1"));
        if (last != null && last.getUserId().equals(user.getId())) {
            throw new BusinessException(500, "Please wait for the technical enginneer to reply");
        }
        long now = System.currentTimeMillis() / 1000;
        TicketMessage tm = new TicketMessage();
        tm.setTicketId(ticket.getId());
        tm.setUserId(user.getId());
        tm.setMessage(message);
        tm.setCreatedAt(now);
        tm.setUpdatedAt(now);
        if (ticketMessageMapper.insert(tm) <= 0) {
            throw new BusinessException(500, "Ticket reply failed");
        }
        ticket.setReplyStatus(0);
        ticket.setUpdatedAt(now);
        ticketMapper.updateById(ticket);
        return ApiResponse.success(true);
    }

    /**
     * 用户关闭工单，对齐 PHP User\\TicketController::close。
     */
    @PostMapping("/close")
    public ApiResponse<Boolean> close(HttpServletRequest request,
            @RequestParam("id") Long id) {
        if (id == null) {
            throw new BusinessException(500, "Invalid parameter");
        }
        User user = requireUser(request);
        Ticket ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getId, id)
                        .eq(Ticket::getUserId, user.getId()));
        if (ticket == null) {
            throw new BusinessException(500, "Ticket does not exist");
        }
        ticket.setStatus(1);
        if (ticketMapper.updateById(ticket) <= 0) {
            throw new BusinessException(500, "Close failed");
        }
        return ApiResponse.success(true);
    }

    /**
     * 佣金提现工单，对齐 PHP User\\TicketController::withdraw（不含 Telegram 通知）。
     */
    @PostMapping("/withdraw")
    public ApiResponse<Boolean> withdraw(HttpServletRequest request,
            @RequestParam("withdraw_method") String withdrawMethod,
            @RequestParam("withdraw_account") String withdrawAccount) {
        if (withdrawCloseEnable != null && withdrawCloseEnable == 1) {
            throw new BusinessException(500, "user.ticket.withdraw.not_support_withdraw");
        }
        String[] methods = (withdrawMethods != null ? withdrawMethods : "").split(",");
        boolean allowed = false;
        for (String m : methods) {
            if (withdrawMethod.equalsIgnoreCase(m.trim())) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new BusinessException(500, "Unsupported withdrawal method");
        }
        User user = requireUser(request);
        long balanceYuan = (user.getCommissionBalance() != null ? user.getCommissionBalance() : 0L) / 100;
        int limit = withdrawLimit != null ? withdrawLimit : 100;
        if (limit > balanceYuan) {
            throw new BusinessException(500, "The current required minimum withdrawal commission is " + limit);
        }

        long now = System.currentTimeMillis() / 1000;
        Ticket ticket = new Ticket();
        ticket.setUserId(user.getId());
        ticket.setSubject("[Commission Withdrawal Request] This ticket is opened by the system");
        ticket.setLevel(2);
        ticket.setStatus(0);
        ticket.setReplyStatus(0);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        if (ticketMapper.insert(ticket) <= 0) {
            throw new BusinessException(500, "Failed to open ticket");
        }
        String message = "Withdrawal method：" + withdrawMethod + "\r\n" +
                "Withdrawal account：" + withdrawAccount;
        TicketMessage tm = new TicketMessage();
        tm.setTicketId(ticket.getId());
        tm.setUserId(user.getId());
        tm.setMessage(message);
        tm.setCreatedAt(now);
        tm.setUpdatedAt(now);
        if (ticketMessageMapper.insert(tm) <= 0) {
            throw new BusinessException(500, "Failed to open ticket");
        }
        return ApiResponse.success(true);
    }

    private User requireUser(HttpServletRequest request) {
        Object attr = request.getAttribute("user");
        if (attr instanceof User user) {
            return user;
        }
        throw new BusinessException(401, "Unauthenticated");
    }
}
