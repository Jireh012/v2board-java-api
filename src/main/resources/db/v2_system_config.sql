-- 系统配置表，用于管理后台 /config/system 持久化（与 PHP 版 config/v2board.php 对应）
CREATE TABLE IF NOT EXISTS `v2_system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL COMMENT '配置名，如 v2board',
  `value` longtext COMMENT 'JSON 配置内容',
  `created_at` bigint DEFAULT NULL,
  `updated_at` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统配置';
