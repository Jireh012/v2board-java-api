package com.v2board.api.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.TicketMapper;
import com.v2board.api.mapper.TicketMessageMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.Ticket;
import com.v2board.api.model.TicketMessage;
import com.v2board.api.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/ticket")
public class AdminTicketController {

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private TicketMessageMapper ticketMessageMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * 管理端工单列表/详情，对齐 PHP Admin\\TicketController::fetch。
     */
    @GetMapping("/fetch")
    public ApiResponse<Object> fetch(@RequestParam(value = "id", required = false) Long id,
                                     @RequestParam(value = "current", required = false, defaultValue = "1") long current,
                                     @RequestParam(value = "pageSize", required = false, defaultValue = "10") long pageSize,
                                     @RequestParam(value = "status", required = false) Integer status,
                                     @RequestParam(value = "reply_status", required = false) List<Integer> replyStatus,
                                     @RequestParam(value = "email", required = false) String email) {
        if (id != null) {
            Ticket ticket = ticketMapper.selectById(id);
            if (ticket == null) {
                throw new BusinessException(500, "工单不存在");
            }
            List<TicketMessage> messages = ticketMessageMapper.selectList(
                    new LambdaQueryWrapper<TicketMessage>()
                            .eq(TicketMessage::getTicketId, ticket.getId())
                            .orderByAsc(TicketMessage::getId)
            );
            for (TicketMessage m : messages) {
                // 管理端视角：非用户消息为 is_me=true
                m.setIsMe(!m.getUserId().equals(ticket.getUserId()));
            }
            ticket.setMessage(messages);
            return ApiResponse.success(ticket);
        }
        if (pageSize < 10) {
            pageSize = 10;
        }
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Ticket::getUpdatedAt);
        if (status != null) {
            wrapper.eq(Ticket::getStatus, status);
        }
        if (replyStatus != null && !replyStatus.isEmpty()) {
            wrapper.in(Ticket::getReplyStatus, replyStatus);
        }
        if (StringUtils.hasText(email)) {
            User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getEmail, email)
            );
            if (user != null) {
                wrapper.eq(Ticket::getUserId, user.getId());
            }
        }
        Page<Ticket> page = new Page<>(current, pageSize);
        Page<Ticket> result = ticketMapper.selectPage(page, wrapper);
        Map<String, Object> resp = new HashMap<>();
        resp.put("data", result.getRecords());
        resp.put("total", result.getTotal());
        return ApiResponse.success(resp);
    }

    /**
     * 管理员回复工单，对齐 PHP Admin\\TicketController::reply（简化版）。
     */
    @PostMapping("/reply")
    public ApiResponse<Boolean> reply(HttpServletRequest request,
                                      @RequestParam("id") Long id,
                                      @RequestParam("message") String message) {
        if (id == null) {
            throw new BusinessException(500, "参数错误");
        }
        if (!StringUtils.hasText(message)) {
            throw new BusinessException(500, "消息不能为空");
        }
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(500, "工单不存在");
        }
        User admin = requireUser(request);
        TicketMessage tm = new TicketMessage();
        tm.setTicketId(ticket.getId());
        tm.setUserId(admin.getId());
        tm.setMessage(message);
        if (ticketMessageMapper.insert(tm) <= 0) {
            throw new BusinessException(500, "回复失败");
        }
        ticket.setReplyStatus(1);
        ticketMapper.updateById(ticket);
        return ApiResponse.success(true);
    }

    /**
     * 管理员关闭工单，对齐 PHP Admin\\TicketController::close。
     */
    @PostMapping("/close")
    public ApiResponse<Boolean> close(@RequestParam("id") Long id) {
        if (id == null) {
            throw new BusinessException(500, "参数错误");
        }
        Ticket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new BusinessException(500, "工单不存在");
        }
        ticket.setStatus(1);
        if (ticketMapper.updateById(ticket) <= 0) {
            throw new BusinessException(500, "关闭失败");
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

