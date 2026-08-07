-- 第三方订阅源与连通节点（手工执行）
CREATE TABLE IF NOT EXISTS `v2_external_subscribe_source` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL COMMENT '源名称',
  `url` varchar(2048) NOT NULL COMMENT '订阅地址',
  `enable` tinyint NOT NULL DEFAULT 0 COMMENT '0禁用 1启用',
  `remark` varchar(512) DEFAULT NULL COMMENT '备注',
  `last_sync_at` bigint DEFAULT NULL COMMENT '上次同步时间(秒)',
  `last_sync_status` varchar(32) DEFAULT NULL COMMENT 'success/failed/running',
  `last_sync_message` varchar(1024) DEFAULT NULL COMMENT '同步摘要或错误信息',
  `created_at` bigint DEFAULT NULL,
  `updated_at` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='第三方订阅源';

CREATE TABLE IF NOT EXISTS `v2_external_subscribe_node` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_id` bigint NOT NULL COMMENT '所属订阅源',
  `name` varchar(255) NOT NULL COMMENT '节点名称',
  `protocol` varchar(64) DEFAULT NULL COMMENT '协议类型',
  `share_uri` text COMMENT '分享链接(可空)',
  `singbox_outbound` longtext NOT NULL COMMENT 'sing-box outbound JSON',
  `fingerprint` varchar(64) NOT NULL COMMENT '同源去重指纹',
  `reachable` tinyint NOT NULL DEFAULT 0 COMMENT '0不通 1连通',
  `last_check_at` bigint DEFAULT NULL COMMENT '上次探测时间(秒)',
  `sort` int DEFAULT 0,
  `created_at` bigint DEFAULT NULL,
  `updated_at` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_fingerprint` (`source_id`, `fingerprint`),
  KEY `idx_source_reachable` (`source_id`, `reachable`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='第三方订阅可达节点缓存';
