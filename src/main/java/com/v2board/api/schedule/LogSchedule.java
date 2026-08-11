package com.v2board.api.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.StatServerMapper;
import com.v2board.api.mapper.StatUserMapper;
import com.v2board.api.mapper.SystemLogMapper;
import com.v2board.api.mapper.UserLoginLogMapper;
import com.v2board.api.model.StatServer;
import com.v2board.api.model.StatUser;
import com.v2board.api.model.SystemLog;
import com.v2board.api.model.UserLoginLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 日志清理定时任务 — 对齐 PHP reset:log
 * 每天凌晨：StatUser/StatServer（~2 个月）、登录记录（~3 个月）、v2_log（1 个月）
 */
@Component
public class LogSchedule {

    private static final Logger logger = LoggerFactory.getLogger(LogSchedule.class);

    @Autowired
    private StatUserMapper statUserMapper;

    @Autowired
    private StatServerMapper statServerMapper;

    @Autowired
    private UserLoginLogMapper userLoginLogMapper;

    @Autowired
    private SystemLogMapper systemLogMapper;

    @Scheduled(cron = "0 0 0 * * ?")
    public void resetLog() {
        try {
            long twoMonthsAgo = System.currentTimeMillis() / 1000 - 60L * 86400; // ~2 个月
            long threeMonthsAgo = System.currentTimeMillis() / 1000 - 90L * 86400;
            long oneMonthAgo = System.currentTimeMillis() / 1000 - 30L * 86400;

            LambdaQueryWrapper<StatUser> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.lt(StatUser::getRecordAt, twoMonthsAgo);
            int userDeleted = statUserMapper.delete(userWrapper);

            LambdaQueryWrapper<StatServer> serverWrapper = new LambdaQueryWrapper<>();
            serverWrapper.lt(StatServer::getRecordAt, twoMonthsAgo);
            int serverDeleted = statServerMapper.delete(serverWrapper);

            int loginDeleted = userLoginLogMapper.delete(
                    new LambdaQueryWrapper<UserLoginLog>().lt(UserLoginLog::getCreatedAt, threeMonthsAgo));

            int systemLogDeleted = systemLogMapper.delete(
                    new LambdaQueryWrapper<SystemLog>().lt(SystemLog::getCreatedAt, oneMonthAgo));

            if (userDeleted > 0 || serverDeleted > 0 || loginDeleted > 0 || systemLogDeleted > 0) {
                logger.info("resetLog: deleted {} StatUser, {} StatServer, {} login logs, {} system logs",
                        userDeleted, serverDeleted, loginDeleted, systemLogDeleted);
            }
        } catch (Exception e) {
            logger.error("LogSchedule failed", e);
        }
    }
}
