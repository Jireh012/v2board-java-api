package com.v2board.api.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.StatServerMapper;
import com.v2board.api.mapper.StatUserMapper;
import com.v2board.api.model.StatServer;
import com.v2board.api.model.StatUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 日志清理定时任务 — 对齐 PHP reset:log
 * 每天凌晨执行：删除 2 个月前的 StatUser 和 StatServer 记录
 */
@Component
public class LogSchedule {

    private static final Logger logger = LoggerFactory.getLogger(LogSchedule.class);

    @Autowired
    private StatUserMapper statUserMapper;

    @Autowired
    private StatServerMapper statServerMapper;

    @Scheduled(cron = "0 0 0 * * ?")
    public void resetLog() {
        try {
            long twoMonthsAgo = System.currentTimeMillis() / 1000 - 60L * 86400; // ~2 个月

            LambdaQueryWrapper<StatUser> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.lt(StatUser::getRecordAt, twoMonthsAgo);
            int userDeleted = statUserMapper.delete(userWrapper);

            LambdaQueryWrapper<StatServer> serverWrapper = new LambdaQueryWrapper<>();
            serverWrapper.lt(StatServer::getRecordAt, twoMonthsAgo);
            int serverDeleted = statServerMapper.delete(serverWrapper);

            if (userDeleted > 0 || serverDeleted > 0) {
                logger.info("resetLog: deleted {} StatUser, {} StatServer records", userDeleted, serverDeleted);
            }
        } catch (Exception e) {
            logger.error("LogSchedule failed", e);
        }
    }
}
