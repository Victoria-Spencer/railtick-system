/*
 Navicat Premium Dump SQL

 Source Server         : mysql92
 Source Server Type    : MySQL
 Source Server Version : 90200 (9.2.0)
 Source Host           : 192.168.43.143:3306
 Source Schema         : rs-ticket

 Target Server Type    : MySQL
 Target Server Version : 90200 (9.2.0)
 File Encoding         : 65001

 Date: 14/07/2026 16:43:48
*/

CREATE DATABASE IF NOT EXISTS `rs-ticket` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `rs-ticket`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for seat
-- ----------------------------
DROP TABLE IF EXISTS `seat`;
CREATE TABLE `seat`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `train_seat_class_id` bigint NOT NULL COMMENT '物理外键：关联列车-席别表train_seat_class.id',
  `seat_no` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '座位号（如1A、2B）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '座位状态（0=完全可选 1=部分区间占用 2=全区间已售）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_seat_no`(`train_seat_class_id` ASC, `seat_no` ASC) USING BTREE,
  INDEX `idx_train_seat_class_id`(`train_seat_class_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7501 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '具体座位表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for seat_class
-- ----------------------------
DROP TABLE IF EXISTS `seat_class`;
CREATE TABLE `seat_class`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `type` int NOT NULL DEFAULT 0,
  `name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '席别名称（如商务座、一等座）',
  `price` decimal(10, 2) NOT NULL COMMENT '席别基础价',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_seat_class_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 31 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '席别字典表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for seat_interval_occupy
-- ----------------------------
DROP TABLE IF EXISTS `seat_interval_occupy`;
CREATE TABLE `seat_interval_occupy`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `seat_id` bigint NOT NULL COMMENT '逻辑外键：关联具体座位表train_seat.id',
  `start_sequence` int NOT NULL COMMENT '区间起点站序（对应train_stop_station.sequence）',
  `end_sequence` int NOT NULL COMMENT '区间终点站序（对应train_stop_station.sequence）',
  `order_id` bigint NOT NULL COMMENT '逻辑外键：关联订单表order.id',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '占用状态（0=预订单临时锁定，1=正式订单临时锁定，2=已支付/永久锁定，3=已释放 / 取消订单）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '锁定过期时间（仅status=1时有效）',
  `lock_id` bigint NOT NULL COMMENT '座位锁唯一标识(一次锁座共用一个)，用于消息幂等、事件溯源',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_seat_id`(`seat_id` ASC) USING BTREE,
  INDEX `idx_order_id`(`order_id` ASC) USING BTREE,
  INDEX `uk_seat_interval_occupy_lock_id`(`lock_id` ASC) USING BTREE,
  CONSTRAINT `seat_interval_occupy_chk_1` CHECK (`start_sequence` < `end_sequence`),
  CONSTRAINT `seat_interval_occupy_chk_2` CHECK (((`status` in (0,1)) and (`expire_time` is not null)) or ((`status` not in (0,1)) and (`expire_time` is null)))
) ENGINE = InnoDB AUTO_INCREMENT = 2073015701152600065 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '座位区间占用表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for station
-- ----------------------------
DROP TABLE IF EXISTS `station`;
CREATE TABLE `station`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '站点id（主键）',
  `query_type` int NULL DEFAULT 0 COMMENT '查询类型（0：热门 1：A-E 2：F-J 3：K-O 4：P-T 5：U-Z）',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '名称（城市、站点、拼音）',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '站点编码（唯一标识）',
  `spell` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '站点名称拼音',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE COMMENT '站点编码唯一',
  INDEX `idx_query_type`(`query_type` ASC) USING BTREE COMMENT '查询类型索引（优化按类型筛选效率）',
  INDEX `idx_name`(`name` ASC) USING BTREE COMMENT '名称索引（优化按名称搜索效率）'
) ENGINE = InnoDB AUTO_INCREMENT = 201 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '站点信息表（存储城市、车站等站点数据）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for train
-- ----------------------------
DROP TABLE IF EXISTS `train`;
CREATE TABLE `train`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `train_number` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '车次',
  `train_attributes` json NULL COMMENT '无用：列车属性（JSON格式）',
  `days_arrived` tinyint NOT NULL COMMENT '跨天数量',
  `sale_time` datetime NULL DEFAULT NULL COMMENT '售票时间',
  `sale_status` tinyint NOT NULL COMMENT '销售状态（0：未开售 1：可售 2：已停售）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_train_number`(`train_number` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 101 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '列车表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for train_seat_class
-- ----------------------------
DROP TABLE IF EXISTS `train_seat_class`;
CREATE TABLE `train_seat_class`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `train_id` bigint NOT NULL COMMENT '逻辑外键：关联列车表train.id',
  `seat_class_id` int NOT NULL COMMENT '物理外键：关联席别表seat_class.id',
  `carriage_number` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '车厢号（如1、2）',
  `total_seats` int NOT NULL COMMENT '该车厢该席别的总座位数',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_train_carriage_class`(`train_id` ASC, `carriage_number` ASC, `seat_class_id` ASC) USING BTREE,
  INDEX `idx_train_id`(`train_id` ASC) USING BTREE,
  INDEX `idx_seat_class_id`(`seat_class_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1501 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '列车-席别关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for train_stop_station
-- ----------------------------
DROP TABLE IF EXISTS `train_stop_station`;
CREATE TABLE `train_stop_station`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `train_id` bigint NOT NULL COMMENT '逻辑外键：关联列车表train.id',
  `station_id` bigint NOT NULL COMMENT '逻辑外键：关联站点表station.id',
  `sequence` int NOT NULL COMMENT '站序（1开始，起点站为1）',
  `arrival_time` datetime NULL DEFAULT NULL COMMENT '到站时间（起点站为null）',
  `departure_time` datetime NULL DEFAULT NULL COMMENT '发车时间（终点站为null）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_train_sequence`(`train_id` ASC, `sequence` ASC) USING BTREE,
  UNIQUE INDEX `uk_train_station`(`train_id` ASC, `station_id` ASC) USING BTREE,
  INDEX `idx_train_id`(`train_id` ASC) USING BTREE,
  INDEX `idx_station_id`(`station_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 733 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '列车经停站关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for train_train_type
-- ----------------------------
DROP TABLE IF EXISTS `train_train_type`;
CREATE TABLE `train_train_type`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `train_id` bigint NOT NULL COMMENT '逻辑外键：关联train表的id',
  `type_id` bigint NOT NULL COMMENT '逻辑外键：关联train_type_dict表的id',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_train_type`(`train_id` ASC, `type_id` ASC) USING BTREE COMMENT '列车-类型组合唯一（避免重复关联）',
  INDEX `idx_train_id`(`train_id` ASC) USING BTREE COMMENT '列车ID索引（优化关联查询效率）',
  INDEX `idx_type_id`(`type_id` ASC) USING BTREE COMMENT '类型ID索引（优化按类型筛选效率）'
) ENGINE = InnoDB AUTO_INCREMENT = 101 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '列车-车次类型中间表（逻辑外键关联train和train_type_dict）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for train_type_dict
-- ----------------------------
DROP TABLE IF EXISTS `train_type_dict`;
CREATE TABLE `train_type_dict`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '类型ID',
  `type_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '类型名称（如“高铁/城际”）',
  `type_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '类型编码（如“GC”）',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '类型描述',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '车次类型字典表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for undo_log
-- ----------------------------
DROP TABLE IF EXISTS `undo_log`;
CREATE TABLE `undo_log`  (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE INDEX `ux_undo_log`(`xid` ASC, `branch_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AT transaction mode undo table' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
