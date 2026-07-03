CREATE TABLE IF NOT EXISTS `local_message_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `invoke_json` JSON NOT NULL COMMENT '需要可靠执行的方法快照，包含类名、方法名、参数类型和参数值',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1=等待执行/等待重试，2=最终失败',
  `next_retry_time` DATETIME(3) NOT NULL COMMENT '下一次允许重试的时间',
  `retry_times` INT NOT NULL DEFAULT 0 COMMENT '已经失败的次数',
  `max_retry_times` INT NOT NULL DEFAULT 3 COMMENT '最大失败次数，达到后进入最终失败状态',
  `fail_reason` VARCHAR(2000) DEFAULT NULL COMMENT '最后一次失败原因，便于排查问题',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_local_message_status_retry` (`status`, `next_retry_time`),
  KEY `idx_local_message_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='本地消息表/可靠执行记录';
