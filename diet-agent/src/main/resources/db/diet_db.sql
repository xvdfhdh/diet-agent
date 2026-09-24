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
  `requested_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STANDARD',
  `actual_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STANDARD',
  `fallback_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `tool_call_count` int NOT NULL DEFAULT 0,
  `agent_task_type` varchar(32) NULL,
  `source_strategy` varchar(24) NOT NULL DEFAULT 'SELECTED_ONLY',
  `repair_count` int NOT NULL DEFAULT 0,
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
  INDEX `idx_trace_execution_mode`(`requested_mode` ASC, `actual_mode` ASC, `created_at` ASC) USING BTREE,
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
  `context_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `summary_through_message_id` bigint NULL DEFAULT NULL,
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
INSERT INTO `diet_slot_option` VALUES (68, 'taste', '清淡', 10, 1, '2026-06-28 17:37:55', '2026-06-28 17:37:55');
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
  `image_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `meal_time` json NOT NULL,
  `mood` json NOT NULL,
  `scene` json NOT NULL,
  `health_goal` json NOT NULL,
  `cuisine` json NOT NULL,
  `taste` json NOT NULL,
  `convenience` json NOT NULL,
  `acquisition_mode` varchar(16) NOT NULL DEFAULT 'BOTH',
  `prep_minutes` int NULL,
  `difficulty` varchar(16) NULL,
  `price_min` decimal(10,2) NULL,
  `price_max` decimal(10,2) NULL,
  `default_servings` int NOT NULL DEFAULT 1,
  `ingredients_json` json NOT NULL,
  `steps_json` json NOT NULL,
  `dine_out_tips` varchar(1000) NULL,
  `substitutes_json` json NOT NULL,
  `calories` int NULL,
  `protein` decimal(8,2) NULL,
  `fat` decimal(8,2) NULL,
  `carbs` decimal(8,2) NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_public_meal_source`(`source_type` ASC) USING BTREE,
  INDEX `idx_private_meal_source`(`owner_user_id` ASC, `source_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of meal_item
-- ----------------------------
INSERT INTO `meal_item` (`id`,`source_type`,`owner_user_id`,`name`,`image_url`,`meal_time`,`mood`,`scene`,`health_goal`,`cuisine`,`taste`,`convenience`,`acquisition_mode`,`prep_minutes`,`difficulty`,`price_min`,`price_max`,`default_servings`,`ingredients_json`,`steps_json`,`dine_out_tips`,`substitutes_json`,`calories`,`protein`,`fat`,`carbs`,`created_at`,`updated_at`) VALUES
(1,'PUBLIC',NULL,'番茄鸡蛋面','/meals/tomato-egg-noodles.jpg','[\"午餐\",\"晚餐\",\"三餐\"]','[\"疲惫\",\"低落\"]','[\"工作\",\"校园\",\"家里\"]','[\"清淡\",\"养胃\",\"易消化\"]','[\"家常\",\"粉面\"]','[\"清淡\",\"番茄味\"]','[\"快速\",\"一人食\"]','COOK',15,'简单',8,15,1,'[{\"name\":\"番茄\",\"category\":\"蔬菜\",\"quantity\":1,\"unit\":\"个\"},{\"name\":\"鸡蛋\",\"category\":\"肉蛋奶\",\"quantity\":2,\"unit\":\"个\"},{\"name\":\"面条\",\"category\":\"主食\",\"quantity\":120,\"unit\":\"克\"}]','[\"番茄切块，鸡蛋打散\",\"炒熟鸡蛋后加入番茄\",\"煮面并浇上番茄鸡蛋\"]',NULL,'[]',520,22,15,72,'2026-06-28 17:37:55','2026-06-28 17:37:55'),
(2,'PUBLIC',NULL,'清汤馄饨','/meals/clear-wonton.jpg','[\"早餐\",\"午餐\",\"晚餐\",\"三餐\"]','[\"疲惫\",\"没胃口\"]','[\"工作\",\"校园\",\"家里\"]','[\"清淡\",\"养胃\",\"暖胃\"]','[\"小吃\",\"粥汤\"]','[\"清淡\",\"咸鲜\"]','[\"快速\",\"少餐具\"]','BOTH',10,'简单',10,22,1,'[{\"name\":\"馄饨\",\"category\":\"主食\",\"quantity\":12,\"unit\":\"个\"}]','[\"水开下馄饨\",\"煮熟后加入清汤和葱花\"]','点清汤底，少油少辣。','[\"清汤水饺\",\"小馄饨\"]',420,18,12,58,'2026-06-28 17:37:55','2026-06-28 17:37:55'),
(3,'PUBLIC',NULL,'鸡胸肉轻食碗','/meals/chicken-grain-bowl.jpg','[\"午餐\",\"晚餐\"]','[\"平静\",\"想放松\"]','[\"工作\",\"运动后\"]','[\"减脂\",\"高蛋白\",\"低油\",\"均衡\"]','[\"轻食\"]','[\"清淡\",\"咸鲜\"]','[\"快速\",\"一人食\"]','BOTH',20,'简单',18,35,1,'[{\"name\":\"鸡胸肉\",\"category\":\"肉蛋奶\",\"quantity\":150,\"unit\":\"克\"},{\"name\":\"生菜\",\"category\":\"蔬菜\",\"quantity\":100,\"unit\":\"克\"}]','[\"煎熟鸡胸肉\",\"搭配蔬菜和主食装碗\"]','酱汁分开放，优先选杂粮主食。','[\"牛肉轻食碗\",\"金枪鱼沙拉\"]',480,42,14,45,'2026-06-28 17:37:55','2026-06-28 17:37:55'),
(4,'PUBLIC',NULL,'麻辣香锅','/meals/spicy-dry-pot.png','[\"午餐\",\"晚餐\",\"夜宵\"]','[\"开心\",\"想奖励自己\"]','[\"周末\",\"聚餐\",\"夜宵\"]','[\"均衡\",\"补能\"]','[\"川菜\",\"小吃\"]','[\"麻辣\",\"烟火气\"]','[\"慢享\",\"多人共享\"]','EAT_OUT',NULL,NULL,35,80,2,'[]','[]','多选蔬菜和瘦肉，少选加工丸类，可要求少油。','[\"冒菜\",\"麻辣烫\"]',NULL,NULL,NULL,NULL,'2026-06-28 17:37:55','2026-06-28 17:37:55'),
(5,'PERSONAL',1,'土豆炖牛肉','/meals/beef-potato-stew.png','[\"晚餐\"]','[\"平静\"]','[\"校园\"]','[\"补能\"]','[\"湘菜\"]','[\"辣\"]','[]','COOK',70,'适中',25,45,2,'[{\"name\":\"牛肉\",\"category\":\"肉蛋奶\",\"quantity\":400,\"unit\":\"克\"},{\"name\":\"土豆\",\"category\":\"蔬菜\",\"quantity\":2,\"unit\":\"个\"}]','[\"牛肉焯水\",\"加入土豆炖至软烂\"]',NULL,'[]',NULL,NULL,NULL,NULL,'2026-07-01 23:22:49','2026-07-01 23:22:49');

-- ----------------------------
-- Table structure for diet_model_config
-- API Key 仅由服务端读写，配置查询接口永不返回明文。
-- ----------------------------
DROP TABLE IF EXISTS `diet_model_config`;
CREATE TABLE `diet_model_config` (
  `id` bigint NOT NULL,
  `provider_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `base_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `endpoint_path` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '/chat/completions',
  `main_model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `light_model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `api_key` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for diet_user
-- ----------------------------
DROP TABLE IF EXISTS `diet_user`;
CREATE TABLE `diet_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER',
  `token_version` int NOT NULL DEFAULT 0,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_diet_user_username` (`username` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1000 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for diet_recommendation_history
-- ----------------------------
DROP TABLE IF EXISTS `diet_recommendation_history`;
CREATE TABLE `diet_recommendation_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `trace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_strategy` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SELECTED_ONLY',
  `user_input` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `slots_json` json NOT NULL,
  `speech_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `meals_json` json NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_recommendation_trace` (`trace_id` ASC) USING BTREE,
  INDEX `idx_recommendation_user_time` (`user_id` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_recommendation_session` (`session_id` ASC, `created_at` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for diet_user_memory
-- ----------------------------
DROP TABLE IF EXISTS `diet_user_memory`;
CREATE TABLE `diet_user_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `memory_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `memory_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `memory_value` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `strength` decimal(5,2) NOT NULL DEFAULT 1.00,
  `evidence_count` int NOT NULL DEFAULT 1,
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_memory` (`user_id` ASC, `memory_type` ASC, `memory_key` ASC, `memory_value` ASC) USING BTREE,
  INDEX `idx_user_memory_recall` (`user_id` ASC, `memory_type` ASC, `strength` DESC, `updated_at` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for diet_favorite_meal
-- ----------------------------
DROP TABLE IF EXISTS `diet_favorite_meal`;
CREATE TABLE `diet_favorite_meal` (
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

-- ----------------------------
-- Weekly meal plan and execution check-ins
-- ----------------------------
DROP TABLE IF EXISTS `diet_meal_checkin`;
DROP TABLE IF EXISTS `diet_meal_plan`;
CREATE TABLE `diet_meal_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT, `user_id` bigint NOT NULL, `plan_date` date NOT NULL,
  `meal_period` varchar(16) NOT NULL, `meal_id` bigint NOT NULL, `meal_snapshot` json NOT NULL,
  `acquisition_mode` varchar(16) NOT NULL, `servings` int NOT NULL DEFAULT 1,
  `status` varchar(16) NOT NULL DEFAULT 'PLANNED', `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_plan_slot` (`user_id`,`plan_date`,`meal_period`),
  KEY `idx_plan_week` (`user_id`,`plan_date`), CONSTRAINT `fk_plan_user` FOREIGN KEY (`user_id`) REFERENCES `diet_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `diet_meal_checkin` (
  `id` bigint NOT NULL AUTO_INCREMENT, `user_id` bigint NOT NULL, `plan_id` bigint NOT NULL,
  `actual_meal_id` bigint NULL, `actual_meal_name` varchar(128) NULL, `rating` int NULL, `satiety` int NULL,
  `reason_code` varchar(32) NULL, `note` varchar(500) NULL, `actual_spent` decimal(10,2) NULL,
  `eaten_at` datetime NULL, `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_checkin_plan` (`plan_id`), KEY `idx_checkin_user_time` (`user_id`,`eaten_at`),
  CONSTRAINT `fk_checkin_plan` FOREIGN KEY (`plan_id`) REFERENCES `diet_meal_plan` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_checkin_user` FOREIGN KEY (`user_id`) REFERENCES `diet_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Shopping list: generated items can be rebuilt without deleting manual items
-- ----------------------------
DROP TABLE IF EXISTS `diet_shopping_item`;
DROP TABLE IF EXISTS `diet_shopping_list`;
CREATE TABLE `diet_shopping_list` (
  `id` bigint NOT NULL AUTO_INCREMENT, `user_id` bigint NOT NULL, `week_start` date NOT NULL,
  `sync_version` int NOT NULL DEFAULT 0, `status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `synced_at` datetime NULL, `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_shopping_week` (`user_id`,`week_start`),
  CONSTRAINT `fk_shopping_user` FOREIGN KEY (`user_id`) REFERENCES `diet_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `diet_shopping_item` (
  `id` bigint NOT NULL AUTO_INCREMENT, `list_id` bigint NOT NULL, `user_id` bigint NOT NULL,
  `name` varchar(128) NOT NULL, `category` varchar(64) NOT NULL, `quantity` decimal(12,3) NULL,
  `unit` varchar(32) NULL, `source_meal_ids` json NOT NULL, `manual` tinyint(1) NOT NULL DEFAULT 0,
  `completed` tinyint(1) NOT NULL DEFAULT 0, `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`), KEY `idx_shopping_items` (`user_id`,`list_id`,`completed`),
  CONSTRAINT `fk_shopping_item_list` FOREIGN KEY (`list_id`) REFERENCES `diet_shopping_list` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_shopping_item_user` FOREIGN KEY (`user_id`) REFERENCES `diet_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Agent V2 staged actions requiring user confirmation
-- ----------------------------
DROP TABLE IF EXISTS `diet_agent_pending_action`;
CREATE TABLE `diet_agent_pending_action` (
  `id` varchar(64) NOT NULL, `user_id` bigint NOT NULL, `session_id` varchar(64) NOT NULL,
  `task_type` varchar(32) NOT NULL, `action_json` json NOT NULL, `preview_json` json NOT NULL,
  `precondition_hash` varchar(64) NOT NULL, `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `expires_at` datetime NOT NULL, `created_at` datetime NOT NULL, `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`), KEY `idx_pending_action_session` (`user_id`,`session_id`,`status`,`created_at`),
  KEY `idx_pending_action_expiry` (`status`,`expires_at`),
  CONSTRAINT `fk_pending_action_user` FOREIGN KEY (`user_id`) REFERENCES `diet_user` (`id`),
  CONSTRAINT `fk_pending_action_session` FOREIGN KEY (`session_id`) REFERENCES `diet_sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
