/*
 Navicat Premium Dump SQL

 Source Server         : 本地MySQL
 Source Server Type    : MySQL
 Source Server Version : 80400 (8.4.0)
 Source Host           : localhost:3306
 Source Schema         : diet_db

 Target Server Type    : MySQL
 Target Server Version : 80400 (8.4.0)
 File Encoding         : 65001

 Date: 13/07/2026 20:24:38
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for diet_messages
-- ----------------------------
DROP TABLE IF EXISTS `diet_messages`;
CREATE TABLE `diet_messages`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `intent` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `agent_trace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_message_session`(`session_id` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 39 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of diet_messages
-- ----------------------------

-- ----------------------------
-- Table structure for diet_request_trace
-- ----------------------------
DROP TABLE IF EXISTS `diet_request_trace`;
CREATE TABLE `diet_request_trace`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `trace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_count` int NOT NULL DEFAULT 0,
  `duration_ms` bigint NULL DEFAULT NULL,
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `trace_json` json NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `expected_intent` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `expected_slots` json NULL,
  `expected_clarify_action` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `labeled_by` bigint NULL DEFAULT NULL,
  `labeled_at` datetime NULL DEFAULT NULL,
  `label_note` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_request_trace`(`trace_id` ASC) USING BTREE,
  INDEX `idx_request_trace_session`(`session_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_request_trace_user`(`user_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_request_trace_status`(`status` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_request_trace_label`(`expected_intent` ASC, `labeled_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 20 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of diet_request_trace
-- ----------------------------

-- ----------------------------
-- Table structure for diet_sessions
-- ----------------------------
DROP TABLE IF EXISTS `diet_sessions`;
CREATE TABLE `diet_sessions`  (
  `id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `phase` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `slots` json NOT NULL,
  `last_recommendations` json NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_session_user`(`user_id` ASC, `updated_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of diet_sessions
-- ----------------------------

-- ----------------------------
-- Table structure for diet_slot_option
-- ----------------------------
DROP TABLE IF EXISTS `diet_slot_option`;
CREATE TABLE `diet_slot_option`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `slot_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `option_value` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_slot_option`(`slot_name` ASC, `option_value` ASC) USING BTREE,
  INDEX `idx_slot_enabled`(`slot_name` ASC, `enabled` ASC, `sort_order` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 281 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of diet_slot_option
-- ----------------------------
INSERT INTO `diet_slot_option` VALUES (1, 'mealTime', '早餐', 10, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (2, 'mealTime', '早午餐', 20, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (3, 'mealTime', '午餐', 30, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (4, 'mealTime', '下午茶', 40, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (5, 'mealTime', '晚餐', 50, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (6, 'mealTime', '夜宵', 60, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (7, 'mealTime', '加餐', 70, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (8, 'mealTime', '三餐', 80, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (9, 'mood', '疲惫', 10, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (10, 'mood', '烦躁', 20, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (11, 'mood', '开心', 30, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (12, 'mood', '焦虑', 40, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (13, 'mood', '低落', 50, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (14, 'mood', '平静', 60, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (15, 'mood', '压力大', 70, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (16, 'mood', '没胃口', 80, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (17, 'mood', '想放松', 90, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (18, 'mood', '想奖励自己', 100, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (19, 'scene', '工作', 10, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (20, 'scene', '校园', 20, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (21, 'scene', '家里', 30, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (22, 'scene', '周末', 40, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (23, 'scene', '加班', 50, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (24, 'scene', '运动后', 60, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (25, 'scene', '通勤', 70, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (26, 'scene', '聚餐', 80, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (27, 'scene', '独处', 90, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (28, 'scene', '旅行', 100, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (30, 'healthGoal', '减脂', 10, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (31, 'healthGoal', '清淡', 20, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (32, 'healthGoal', '养胃', 30, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (33, 'healthGoal', '高蛋白', 40, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (34, 'healthGoal', '均衡', 50, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (35, 'healthGoal', '降火', 60, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (36, 'healthGoal', '低油', 70, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (37, 'healthGoal', '低盐', 80, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (38, 'healthGoal', '低糖', 90, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (39, 'healthGoal', '补能', 100, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (40, 'healthGoal', '增肌', 110, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (41, 'healthGoal', '控碳水', 120, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (42, 'healthGoal', '易消化', 130, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (43, 'healthGoal', '暖胃', 140, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (44, 'cuisine', '川菜', 10, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (45, 'cuisine', '粤菜', 20, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (46, 'cuisine', '湘菜', 30, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (47, 'cuisine', '江浙菜', 40, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (48, 'cuisine', '东北菜', 50, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (49, 'cuisine', '鲁菜', 60, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (50, 'cuisine', '闽南菜', 70, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (51, 'cuisine', '云南菜', 80, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (52, 'cuisine', '新疆菜', 90, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (53, 'cuisine', '轻食', 100, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (54, 'cuisine', '西餐', 110, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (55, 'cuisine', '日料', 120, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (56, 'cuisine', '韩餐', 130, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (57, 'cuisine', '东南亚菜', 140, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (58, 'cuisine', '火锅', 150, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (59, 'cuisine', '烧烤', 160, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (60, 'cuisine', '海鲜', 170, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (61, 'cuisine', '素食', 180, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (62, 'cuisine', '家常', 190, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (63, 'cuisine', '小吃', 200, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (64, 'cuisine', '粉面', 210, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (65, 'cuisine', '粥汤', 220, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (66, 'cuisine', '快餐', 230, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (67, 'cuisine', '甜品', 240, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (69, 'taste', '辣', 20, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (70, 'taste', '微辣', 30, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (71, 'taste', '中辣', 40, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (72, 'taste', '麻辣', 50, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (73, 'taste', '甜', 60, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (74, 'taste', '酸甜', 70, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (75, 'taste', '咸鲜', 80, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (76, 'taste', '鲜香', 90, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (77, 'taste', '酱香', 100, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (78, 'taste', '蒜香', 110, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (79, 'taste', '番茄味', 120, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (80, 'taste', '咖喱味', 130, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (81, 'taste', '奶香', 140, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (82, 'taste', '油香', 150, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (83, 'taste', '烟火气', 160, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (84, 'convenience', '快速', 10, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (85, 'convenience', '慢享', 20, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (86, 'convenience', '外带方便', 30, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (87, 'convenience', '堂食舒服', 40, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (88, 'convenience', '少排队', 50, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (89, 'convenience', '少餐具', 60, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (90, 'convenience', '一人食', 70, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (91, 'convenience', '多人共享', 80, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (92, 'convenience', '适合备餐', 90, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `diet_slot_option` VALUES (93, 'convenience', '适合边走边吃', 100, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');

-- ----------------------------
-- Table structure for meal_item
-- ----------------------------
DROP TABLE IF EXISTS `meal_item`;
CREATE TABLE `meal_item`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_user_id` bigint NULL DEFAULT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `meal_time` json NOT NULL,
  `mood` json NOT NULL,
  `scene` json NOT NULL,
  `health_goal` json NOT NULL,
  `cuisine` json NOT NULL,
  `taste` json NOT NULL,
  `convenience` json NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_public_meal_source`(`source_type` ASC) USING BTREE,
  INDEX `idx_private_meal_source`(`owner_user_id` ASC, `source_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of meal_item
-- ----------------------------
INSERT INTO `meal_item` VALUES (1, 'PUBLIC', NULL, '番茄鸡蛋面', '[\"午餐\", \"晚餐\", \"三餐\"]', '[\"疲惫\", \"低落\"]', '[\"工作\", \"校园\", \"家里\"]', '[\"清淡\", \"养胃\", \"易消化\"]', '[\"家常\", \"粉面\"]', '[\"清淡\", \"番茄味\"]', '[\"快速\", \"一人食\"]', '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `meal_item` VALUES (2, 'PUBLIC', NULL, '清汤馄饨', '[\"早餐\", \"午餐\", \"晚餐\", \"三餐\"]', '[\"疲惫\", \"没胃口\"]', '[\"工作\", \"校园\", \"家里\"]', '[\"清淡\", \"养胃\", \"暖胃\"]', '[\"小吃\", \"粥汤\"]', '[\"清淡\", \"咸鲜\"]', '[\"快速\", \"少餐具\"]', '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `meal_item` VALUES (3, 'PUBLIC', NULL, '鸡胸肉轻食碗', '[\"午餐\", \"晚餐\"]', '[\"平静\", \"想放松\"]', '[\"工作\", \"运动后\"]', '[\"减脂\", \"高蛋白\", \"低油\", \"均衡\"]', '[\"轻食\"]', '[\"清淡\", \"咸鲜\"]', '[\"快速\", \"一人食\"]', '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `meal_item` VALUES (4, 'PUBLIC', NULL, '麻辣香锅', '[\"午餐\", \"晚餐\", \"夜宵\"]', '[\"开心\", \"想奖励自己\"]', '[\"周末\", \"聚餐\", \"夜宵\"]', '[\"均衡\", \"补能\"]', '[\"川菜\", \"小吃\"]', '[\"麻辣\", \"烟火气\"]', '[\"慢享\", \"多人共享\"]', '2026-06-28 17:37:55', '2026-06-28 17:37:55');
INSERT INTO `meal_item` VALUES (5, 'PERSONAL', 1, '土豆炖牛肉', '[\"晚餐\"]', '[\"平静\"]', '[\"校园\"]', '[\"补能\"]', '[\"湘菜\"]', '[\"辣\"]', '[]', '2026-07-01 23:22:49', '2026-07-01 23:22:49');

-- ----------------------------
-- Table structure for recommend_feedback
-- ----------------------------
DROP TABLE IF EXISTS `recommend_feedback`;
CREATE TABLE `recommend_feedback`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `item_id` bigint NULL DEFAULT NULL,
  `action` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `rating` int NULL DEFAULT NULL,
  `reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_feedback_user`(`user_id` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of recommend_feedback
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
