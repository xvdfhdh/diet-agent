-- Existing public sample meals contain the taste tag 清淡.
-- Ensure administrators can validate and update those meals on old installations.
INSERT INTO `diet_slot_option` (`slot_name`, `option_value`, `sort_order`, `enabled`, `created_at`, `updated_at`)
VALUES ('taste', '清淡', 10, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `enabled` = 1, `updated_at` = NOW();
