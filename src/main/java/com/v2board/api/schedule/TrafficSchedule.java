package com.v2board.api.schedule;

import com.v2board.api.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 流量刷新定时任务 — 对齐 PHP traffic:update
 * 每分钟从 Redis hash 中读取流量数据，批量更新到数据库
 */
@Component
public class TrafficSchedule {

    private static final Logger logger = LoggerFactory.getLogger(TrafficSchedule.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserMapper userMapper;

    @Scheduled(cron = "0 * * * * ?")
    @Transactional
    public void updateTraffic() {
        try {
            // 检查 traffic_reset_lock（ResetSchedule 执行期间不处理）
            Boolean locked = redisTemplate.hasKey("traffic_reset_lock");
            if (Boolean.TRUE.equals(locked)) {
                logger.debug("traffic_reset_lock is set, skipping traffic update");
                return;
            }

            // 原子读取并删除 Redis hash
            Map<Object, Object> uploadMap = redisTemplate.opsForHash().entries("v2board_upload_traffic");
            Map<Object, Object> downloadMap = redisTemplate.opsForHash().entries("v2board_download_traffic");

            if (uploadMap.isEmpty() && downloadMap.isEmpty()) {
                return;
            }

            // 立即删除，避免重复处理
            redisTemplate.delete("v2board_upload_traffic");
            redisTemplate.delete("v2board_download_traffic");

            // 合并为 trafficMap: userId → [u_increment, d_increment]
            Map<Long, long[]> trafficMap = new HashMap<>();

            for (Map.Entry<Object, Object> entry : uploadMap.entrySet()) {
                try {
                    Long userId = Long.valueOf(String.valueOf(entry.getKey()));
                    long u = toLong(entry.getValue());
                    trafficMap.computeIfAbsent(userId, k -> new long[2])[0] = u;
                } catch (NumberFormatException ignore) {
                }
            }

            for (Map.Entry<Object, Object> entry : downloadMap.entrySet()) {
                try {
                    Long userId = Long.valueOf(String.valueOf(entry.getKey()));
                    long d = toLong(entry.getValue());
                    trafficMap.computeIfAbsent(userId, k -> new long[2])[1] = d;
                } catch (NumberFormatException ignore) {
                }
            }

            if (trafficMap.isEmpty()) {
                return;
            }

            // 批量更新数据库
            long timestamp = System.currentTimeMillis() / 1000;
            int rows = userMapper.batchUpdateTraffic(trafficMap, timestamp);
            logger.info("Traffic update: {} users updated", rows);

        } catch (Exception e) {
            logger.error("TrafficSchedule failed", e);
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
