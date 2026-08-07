-- 用户登录记录（Java 侧新增，供管理端「TA 的登录」查询）
CREATE TABLE IF NOT EXISTS `v2_user_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `created_at` bigint DEFAULT NULL COMMENT 'Unix 秒',
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户登录记录';
