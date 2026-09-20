-- Daily assistant: meal details, weekly plans, check-ins and shopping lists (MySQL 8).
ALTER TABLE `meal_item`
  ADD COLUMN `acquisition_mode` varchar(16) NOT NULL DEFAULT 'BOTH' AFTER `convenience`,
  ADD COLUMN `prep_minutes` int NULL AFTER `acquisition_mode`,
  ADD COLUMN `difficulty` varchar(16) NULL AFTER `prep_minutes`,
  ADD COLUMN `price_min` decimal(10,2) NULL AFTER `difficulty`,
  ADD COLUMN `price_max` decimal(10,2) NULL AFTER `price_min`,
  ADD COLUMN `default_servings` int NOT NULL DEFAULT 1 AFTER `price_max`,
  ADD COLUMN `ingredients_json` json NULL AFTER `default_servings`,
  ADD COLUMN `steps_json` json NULL AFTER `ingredients_json`,
  ADD COLUMN `dine_out_tips` varchar(1000) NULL AFTER `steps_json`,
  ADD COLUMN `substitutes_json` json NULL AFTER `dine_out_tips`,
  ADD COLUMN `calories` int NULL AFTER `substitutes_json`,
  ADD COLUMN `protein` decimal(8,2) NULL AFTER `calories`,
  ADD COLUMN `fat` decimal(8,2) NULL AFTER `protein`,
  ADD COLUMN `carbs` decimal(8,2) NULL AFTER `fat`;

UPDATE `meal_item`
SET `ingredients_json` = JSON_ARRAY(), `steps_json` = JSON_ARRAY(), `substitutes_json` = JSON_ARRAY()
WHERE `ingredients_json` IS NULL OR `steps_json` IS NULL OR `substitutes_json` IS NULL;

ALTER TABLE `meal_item`
  MODIFY COLUMN `ingredients_json` json NOT NULL,
  MODIFY COLUMN `steps_json` json NOT NULL,
  MODIFY COLUMN `substitutes_json` json NOT NULL;

CREATE TABLE IF NOT EXISTS `diet_meal_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `plan_date` date NOT NULL,
  `meal_period` varchar(16) NOT NULL,
  `meal_id` bigint NOT NULL,
  `meal_snapshot` json NOT NULL,
  `acquisition_mode` varchar(16) NOT NULL,
  `servings` int NOT NULL DEFAULT 1,
  `status` varchar(16) NOT NULL DEFAULT 'PLANNED',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_slot` (`user_id`, `plan_date`, `meal_period`),
  INDEX `idx_plan_week` (`user_id`, `plan_date`),
  CONSTRAINT `fk_plan_user` FOREIGN KEY (`user_id`) REFERENCES `diet_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `diet_meal_checkin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `plan_id` bigint NOT NULL,
  `actual_meal_id` bigint NULL,
  `actual_meal_name` varchar(128) NULL,
  `rating` int NULL,
  `satiety` int NULL,
  `reason_code` varchar(32) NULL,
  `note` varchar(500) NULL,
  `actual_spent` decimal(10,2) NULL,
  `eaten_at` datetime NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_checkin_plan` (`plan_id`),
  INDEX `idx_checkin_user_time` (`user_id`, `eaten_at`),
  CONSTRAINT `fk_checkin_plan` FOREIGN KEY (`plan_id`) REFERENCES `diet_meal_plan` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_checkin_user` FOREIGN KEY (`user_id`) REFERENCES `diet_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `diet_shopping_list` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `week_start` date NOT NULL,
  `sync_version` int NOT NULL DEFAULT 0,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `synced_at` datetime NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shopping_week` (`user_id`, `week_start`),
  CONSTRAINT `fk_shopping_user` FOREIGN KEY (`user_id`) REFERENCES `diet_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `diet_shopping_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `list_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL,
  `category` varchar(64) NOT NULL,
  `quantity` decimal(12,3) NULL,
  `unit` varchar(32) NULL,
  `source_meal_ids` json NOT NULL,
  `manual` tinyint(1) NOT NULL DEFAULT 0,
  `completed` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_shopping_items` (`user_id`, `list_id`, `completed`),
  CONSTRAINT `fk_shopping_item_list` FOREIGN KEY (`list_id`) REFERENCES `diet_shopping_list` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_shopping_item_user` FOREIGN KEY (`user_id`) REFERENCES `diet_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
