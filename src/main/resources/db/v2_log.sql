-- PHP 兼容系统错误日志表（MysqlLoggerHandler → v2_log）
CREATE TABLE IF NOT EXISTS `v2_log` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `title` text NOT NULL,
  `level` varchar(11) DEFAULT NULL,
  `host` varchar(255) DEFAULT NULL,
  `uri` varchar(255) NOT NULL,
  `method` varchar(11) NOT NULL,
  `data` text,
  `ip` varchar(128) DEFAULT NULL,
  `context` text,
  `created_at` int(11) NOT NULL,
  `updated_at` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
