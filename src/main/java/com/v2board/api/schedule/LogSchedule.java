package com.v2board.api.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.mapper.StatServerMapper;
import com.v2board.api.mapper.StatUserMapper;
import com.v2board.api.mapper.UserLoginLogMapper;
import com.v2board.api.model.StatServer;
import com.v2board.api.model.StatUser;
import com.v2board.api.model.UserLoginLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 日志清理定时任务 — 对齐 PHP reset:log
 * 每天凌晨执行：删除 2 个月前的 StatUser / StatServer，以及 3 个月前的登录记录
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

    @Scheduled(cron = "0 0 0 * * ?")
    public void resetLog() {
        try {
            long twoMonthsAgo = System.currentTimeMillis() / 1000 - 60L * 86400; // ~2 个月
            long threeMonthsAgo = System.currentTimeMillis() / 1000 - 90L * 86400;

            LambdaQueryWrapper<StatUser> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.lt(StatUser::getRecordAt, twoMonthsAgo);
            int userDeleted = statUserMapper.delete(userWrapper);

            LambdaQueryWrapper<StatServer> serverWrapper = new LambdaQueryWrapper<>();
            serverWrapper.lt(StatServer::getRecordAt, twoMonthsAgo);
            int serverDeleted = statServerMapper.delete(serverWrapper);

            int loginDeleted = userLoginLogMapper.delete(
                    new LambdaQueryWrapper<UserLoginLog>().lt(UserLoginLog::getCreatedAt, threeMonthsAgo));

            if (userDeleted > 0 || serverDeleted > 0 || loginDeleted > 0) {
                logger.info("resetLog: deleted {} StatUser, {} StatServer, {} login logs",
                        userDeleted, serverDeleted, loginDeleted);
            }
        } catch (Exception e) {
            logger.error("LogSchedule failed", e);
        }
    }
}
