/*
 Navicat Premium Dump SQL

 Source Server         : mysql92
 Source Server Type    : MySQL
 Source Server Version : 90200 (9.2.0)
 Source Host           : 192.168.43.143:3306
 Source Schema         : rs-pay

 Target Server Type    : MySQL
 Target Server Version : 90200 (9.2.0)
 File Encoding         : 65001

 Date: 14/07/2026 16:43:29
*/

CREATE DATABASE IF NOT EXISTS `rs-pay` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `rs-pay`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for pay_order
-- ----------------------------
DROP TABLE IF EXISTS `pay_order`;
CREATE TABLE `pay_order`  (
  `id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '支付单id',
  `user_id` bigint NOT NULL COMMENT '归属用户ID（逻辑外键，关联用户表）',
  `channel` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '支付渠道（如：WECHAT-微信支付，ALIPAY-支付宝）',
  `trade_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '支付环境（如：APP-APP支付，H5-H5支付，NATIVE-扫码支付）',
  `order_sn` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订单号（关联订单表order_sn）',
  `out_order_sn` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '外部订单号（支付平台返回的订单号，用于对账）',
  `total_amount` decimal(10, 2) NOT NULL COMMENT '总金额',
  `refund_amount` decimal(10, 2) NULL DEFAULT NULL COMMENT '实际退款金额（全额退款等于total_amount，部分退款为实际金额）',
  `refund_time` datetime NULL DEFAULT NULL COMMENT '退款完成时间（精确记录退款成功的时间）',
  `pay_status` int NOT NULL COMMENT '支付状态（对应PaymentStatus枚举：0-未支付，1-支付中，2-支付成功，3-支付失败，4-已退款，5部分退款）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_sn`(`order_sn` ASC) USING BTREE COMMENT '订单号唯一，确保一个订单对应一个支付单',
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE COMMENT '用户ID索引，优化按用户查询支付记录的场景'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付订单表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
