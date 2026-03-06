package com.v2board.api.schedule;

import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.User;
import com.v2board.api.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 邮件提醒定时任务 — 对齐 PHP send:remindMail
 * 每天 11:30 执行：发送到期提醒和流量提醒邮件
 */
@Component
public class RemindSchedule {

    private static final Logger logger = LoggerFactory.getLogger(RemindSchedule.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MailService mailService;

    @Scheduled(cron = "0 30 11 * * ?")
    public void sendRemindMail() {
        try {
            List<User> users = userMapper.selectList(null);
            int expireCount = 0;
            int trafficCount = 0;

            long now = System.currentTimeMillis() / 1000;

            for (User user : users) {
                // 到期提醒
                if (user.getRemindExpire() != null && user.getRemindExpire() == 1) {
                    mailService.remindExpire(user);
                    expireCount++;
                }

                // 流量提醒（未过期用户）
                if (user.getRemindTraffic() != null && user.getRemindTraffic() == 1) {
                    if (user.getExpiredAt() == null || user.getExpiredAt() > now) {
                        mailService.remindTraffic(user);
                        trafficCount++;
                    }
                }
            }

            logger.info("sendRemindMail: checked {} expire, {} traffic reminders", expireCount, trafficCount);
        } catch (Exception e) {
            logger.error("RemindSchedule failed", e);
        }
    }
}
