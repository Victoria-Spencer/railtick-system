/*
 Navicat Premium Dump SQL

 Source Server         : mysql92
 Source Server Type    : MySQL
 Source Server Version : 90200 (9.2.0)
 Source Host           : 192.168.43.143:3306
 Source Schema         : rs-order

 Target Server Type    : MySQL
 Target Server Version : 90200 (9.2.0)
 File Encoding         : 65001

 Date: 14/07/2026 16:43:16
*/

CREATE DATABASE IF NOT EXISTS `rs-order` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `rs-order`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for order
-- ----------------------------
DROP TABLE IF EXISTS `order`;
CREATE TABLE `order`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单id',
  `user_id` bigint NOT NULL COMMENT '归属用户ID（逻辑外键，关联用户表user.id）',
  `train_id` bigint NOT NULL COMMENT '列车ID（逻辑外键，关联列车表train.id）',
  `order_sn` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订单号',
  `pre_order_sn` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联的预订单号（逻辑外键，关联预订单表pre_order.pre_order_sn，便于追溯来源）',
  `status` int NOT NULL DEFAULT 0 COMMENT '订单状态（对应OrderStatus枚举：0-待支付，1-已支付，2-已取消，3-部分退票，4-全部退票）',
  `total_amount` decimal(10, 2) NOT NULL COMMENT '总金额',
  `pay_time` datetime NULL DEFAULT NULL COMMENT '支付时间（支付完成后更新）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '订单支付过期时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_sn`(`order_sn` ASC) USING BTREE COMMENT '订单号唯一',
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE COMMENT '用户ID索引，用于关联用户表查询',
  INDEX `idx_pre_order_sn`(`pre_order_sn` ASC) USING BTREE COMMENT '预订单号索引，用于关联预订单表查询'
) ENGINE = InnoDB AUTO_INCREMENT = 2073015700745752577 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '正式订单表（支付后生效）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for order_details
-- ----------------------------
DROP TABLE IF EXISTS `order_details`;
CREATE TABLE `order_details`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单明细id',
  `order_id` bigint NOT NULL COMMENT '订单id（逻辑外键，关联正式订单表order.id）',
  `pre_order_detail_id` bigint NULL DEFAULT NULL COMMENT '关联的预订单明细ID（逻辑外键，关联预订单明细表pre_order_details.id，追溯来源）',
  `departure` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '出发站点',
  `departure_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '出发站编码',
  `arrival` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '到达站点',
  `arrival_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '到达站编码',
  `riding_date` datetime NOT NULL COMMENT '乘车日期',
  `train_id` bigint NOT NULL COMMENT '列车车次',
  `departure_time` datetime NOT NULL COMMENT '出发时间',
  `arrival_time` datetime NOT NULL COMMENT '到达时间',
  `seat_type` int NOT NULL COMMENT '席别类型（对应席别枚举：如商务座、一等座等）',
  `carriage_number` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '车厢号',
  `seat_no` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '座位号（如1A、2A）',
  `real_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '真实姓名（乘车人）',
  `id_type` int NOT NULL COMMENT '证件类型（对应枚举：0-居民身份证，1-护照，2-港澳通行证等）',
  `id_card` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '证件号码（如身份证号、护照号等）',
  `ticket_type` int NOT NULL COMMENT '车票类型（对应TicketType枚举：0-成人，1-儿童，2-学生，3-残疾军人）',
  `amount` decimal(10, 2) NOT NULL COMMENT '订单金额（单位：分，避免浮点误差）',
  `refund_status` tinyint NOT NULL DEFAULT 0 COMMENT '退票状态（0-未退票，1-已退票）',
  `refund_time` datetime NULL DEFAULT NULL COMMENT '明细退款时间（部分退票时记录单张车票的退款时间）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order_id`(`order_id` ASC) USING BTREE COMMENT '订单id索引，用于关联正式订单表查询',
  INDEX `idx_pre_order_detail_id`(`pre_order_detail_id` ASC) USING BTREE COMMENT '预订单明细ID索引，用于关联预订单明细表查询'
) ENGINE = InnoDB AUTO_INCREMENT = 134 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '正式订单明细表（生效的乘客及车票信息）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for pre_order
-- ----------------------------
DROP TABLE IF EXISTS `pre_order`;
CREATE TABLE `pre_order`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '预订单ID',
  `pre_order_sn` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '预订单号（唯一标识）',
  `user_id` bigint NOT NULL COMMENT '归属用户ID（逻辑外键，关联用户表user.id）',
  `train_id` bigint NOT NULL COMMENT '列车ID（逻辑外键，关联列车表train.id）',
  `total_amount` decimal(10, 2) NOT NULL COMMENT '总金额（预估）',
  `expire_time` datetime NOT NULL COMMENT '过期时间（如30分钟后自动失效）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '预订单状态（0-有效，1-已过期，2-已转为正式订单）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_pre_order_sn`(`pre_order_sn` ASC) USING BTREE COMMENT '预订单号唯一',
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE COMMENT '用户ID索引，用于关联用户表查询',
  INDEX `idx_train_id`(`train_id` ASC) USING BTREE COMMENT '列车ID索引，用于关联列车表查询',
  INDEX `idx_expire_time`(`expire_time` ASC) USING BTREE COMMENT '过期时间索引，用于定时清理过期预订单'
) ENGINE = InnoDB AUTO_INCREMENT = 152 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '预订单表（临时锁定座位阶段）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for pre_order_details
-- ----------------------------
DROP TABLE IF EXISTS `pre_order_details`;
CREATE TABLE `pre_order_details`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '预订单明细ID',
  `pre_order_id` bigint NOT NULL COMMENT '预订单ID（逻辑外键，关联预订单表pre_order.id）',
  `real_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '乘客姓名',
  `id_type` int NOT NULL COMMENT '证件类型（对应枚举：0-居民身份证，1-护照，2-港澳通行证等）',
  `id_card` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '证件号码（如身份证号、护照号等）',
  `ticket_type` int NOT NULL COMMENT '票种（对应TicketType枚举：0-成人，1-儿童，2-学生，3-残疾军人）',
  `seat_type` int NOT NULL COMMENT '席别类型（对应席别枚举：如商务座、一等座等）',
  `temp_seat_no` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '临时座位号（选座阶段的暂存，可能为空）',
  `carriage_number` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '车厢号（如“01”“12”等）',
  `amount` decimal(10, 2) NOT NULL COMMENT '单张票价（单位：分，避免浮点误差）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_pre_order_id`(`pre_order_id` ASC) USING BTREE COMMENT '预订单ID索引，用于关联预订单表查询'
) ENGINE = InnoDB AUTO_INCREMENT = 315 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '预订单明细表（临时存储乘客及座位信息）' ROW_FORMAT = Dynamic;

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
