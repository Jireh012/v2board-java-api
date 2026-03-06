package com.v2board.api.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.TicketMapper;
import com.v2board.api.model.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工单自动关闭定时任务 — 对齐 PHP check:ticket
 * 每分钟检查已回复但 24 小时无响应的工单，自动关闭
 */
@Component
public class TicketSchedule {

    private static final Logger logger = LoggerFactory.getLogger(TicketSchedule.class);

    @Autowired
    private TicketMapper ticketMapper;

    @Scheduled(cron = "0 * * * * ?")
    public void checkTicket() {
        try {
            long cutoff = System.currentTimeMillis() / 1000 - 86400; // 24 小时前

            LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Ticket::getStatus, 0)           // 打开状态
                    .eq(Ticket::getReplyStatus, 1)      // 管理员已回复
                    .le(Ticket::getUpdatedAt, cutoff);   // 超过 24 小时无响应

            List<Ticket> tickets = ticketMapper.selectList(wrapper);

            for (Ticket ticket : tickets) {
                ticket.setStatus(1); // 关闭
                ticket.setUpdatedAt(System.currentTimeMillis() / 1000);
                ticketMapper.updateById(ticket);
            }

            if (!tickets.isEmpty()) {
                logger.info("checkTicket: auto-closed {} tickets", tickets.size());
            }
        } catch (Exception e) {
            logger.error("TicketSchedule failed", e);
        }
    }
}
