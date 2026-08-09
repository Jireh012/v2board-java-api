-- 后台任务失败记录（Java 队列；非 PHP 表）
CREATE TABLE IF NOT EXISTS `v2_job_failed` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uuid` varchar(36) NOT NULL,
  `queue` varchar(64) NOT NULL,
  `job_type` varchar(64) NOT NULL,
  `payload` mediumtext NOT NULL,
  `exception` text,
  `failed_at` bigint NOT NULL COMMENT 'Unix 秒',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uuid` (`uuid`),
  KEY `idx_queue_failed` (`queue`, `failed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='队列失败任务';
