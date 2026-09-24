-- Agent V2 pending confirmations and source strategy metadata (MySQL 8).
CREATE TABLE IF NOT EXISTS `diet_agent_pending_action` (
  `id` varchar(64) NOT NULL,
  `user_id` bigint NOT NULL,
  `session_id` varchar(64) NOT NULL,
  `task_type` varchar(32) NOT NULL,
  `action_json` json NOT NULL,
  `preview_json` json NOT NULL,
  `precondition_hash` varchar(64) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `expires_at` datetime NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pending_action_session` (`user_id`,`session_id`,`status`,`created_at`),
  KEY `idx_pending_action_expiry` (`status`,`expires_at`),
  CONSTRAINT `fk_pending_action_user` FOREIGN KEY (`user_id`) REFERENCES `diet_user` (`id`),
  CONSTRAINT `fk_pending_action_session` FOREIGN KEY (`session_id`) REFERENCES `diet_sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `diet_recommendation_history`
  ADD COLUMN `source_strategy` varchar(24) NOT NULL DEFAULT 'SELECTED_ONLY' AFTER `source_mode`;

ALTER TABLE `diet_request_trace`
  ADD COLUMN `agent_task_type` varchar(32) NULL AFTER `tool_call_count`,
  ADD COLUMN `source_strategy` varchar(24) NOT NULL DEFAULT 'SELECTED_ONLY' AFTER `agent_task_type`,
  ADD COLUMN `repair_count` int NOT NULL DEFAULT 0 AFTER `source_strategy`;

ALTER TABLE `diet_sessions`
  ADD COLUMN `context_summary` text NULL AFTER `last_recommendations`,
  ADD COLUMN `summary_through_message_id` bigint NULL AFTER `context_summary`;
