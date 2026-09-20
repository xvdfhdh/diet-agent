-- Observability fields for standard/agent recommendation routing (MySQL 8).
ALTER TABLE `diet_request_trace`
  ADD COLUMN `requested_mode` varchar(16) NOT NULL DEFAULT 'STANDARD' AFTER `trace_json`,
  ADD COLUMN `actual_mode` varchar(16) NOT NULL DEFAULT 'STANDARD' AFTER `requested_mode`,
  ADD COLUMN `fallback_code` varchar(64) NULL AFTER `actual_mode`,
  ADD COLUMN `tool_call_count` int NOT NULL DEFAULT 0 AFTER `fallback_code`;

CREATE INDEX `idx_trace_execution_mode`
  ON `diet_request_trace` (`requested_mode`, `actual_mode`, `created_at`);
