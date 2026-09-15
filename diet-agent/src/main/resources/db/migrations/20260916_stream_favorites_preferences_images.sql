-- Existing installations: run once to add meal images and favorites.
ALTER TABLE `meal_item`
  ADD COLUMN `image_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL AFTER `name`;

UPDATE `meal_item` SET `image_url` = '/meals/tomato-egg-noodles.jpg' WHERE `id` = 1 AND `image_url` IS NULL;
UPDATE `meal_item` SET `image_url` = '/meals/clear-wonton.jpg' WHERE `id` = 2 AND `image_url` IS NULL;
UPDATE `meal_item` SET `image_url` = '/meals/chicken-grain-bowl.jpg' WHERE `id` = 3 AND `image_url` IS NULL;
UPDATE `meal_item` SET `image_url` = '/meals/spicy-dry-pot.png' WHERE `id` = 4 AND `image_url` IS NULL;
UPDATE `meal_item` SET `image_url` = '/meals/beef-potato-stew.png' WHERE `id` = 5 AND `image_url` IS NULL;

CREATE TABLE IF NOT EXISTS `diet_favorite_meal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `meal_id` bigint NOT NULL,
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `meal_json` json NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_favorite_user_meal` (`user_id` ASC, `meal_id` ASC) USING BTREE,
  INDEX `idx_favorite_user_time` (`user_id` ASC, `updated_at` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;
