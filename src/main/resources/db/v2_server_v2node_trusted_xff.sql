-- 对齐 wyx2685/v2board 858effa：v2node 信任的 X-Forwarded-For 头部
-- 项目无自动迁移，请在共享 MySQL 上手动执行（PHP update.sql 同步）

ALTER TABLE `v2_server_v2node`
ADD COLUMN `trusted_x_forwarded_for` varchar(255) COLLATE utf8mb4_general_ci NULL
COMMENT '信任的x-forwarded-for头部' AFTER `network_settings`;
