-- 订阅规则模板（手工执行）
-- 管理员可同步上游 / 手动编辑 / 恢复默认；订阅 Builder 经 Redis → DB → classpath 解析。
CREATE TABLE IF NOT EXISTS `v2_subscribe_rule_template` (
  `format` varchar(32) NOT NULL COMMENT 'clash/stash/surge/surfboard/singbox/quantumultx/loon',
  `content` longtext NOT NULL COMMENT '规则模板正文',
  `source_url` varchar(2048) DEFAULT NULL COMMENT '同步源 URL',
  `update_source` varchar(32) DEFAULT NULL COMMENT 'manual/sync/restore',
  `updated_at` bigint DEFAULT NULL COMMENT '更新时间(秒)',
  `created_at` bigint DEFAULT NULL COMMENT '创建时间(秒)',
  PRIMARY KEY (`format`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订阅规则模板';
