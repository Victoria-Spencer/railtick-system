package org.rail.payservice.pojo.entity;

import org.rail.payservice.enums.PaymentStatus;

import java.math.BigDecimal;

public class PayOrder {

    // 支付单id
    private String id;
    // userId，归属用户（逻辑外键）
    private Long userId;
    // 支付渠道
    private String channel;
    // 支付环境
    private String tradeType;
    // 订单号
    private String orderSn;
    // 外部订单号，支付平台（如微信支付、支付宝）返回的对应支付订单号，用于对账或追踪支付状态。
    private String outOrderSn;
//    // 主体
//    private String subject;
    // 总金额
    private BigDecimal totalAmount;
    // 支付状态
    private PaymentStatus payStatus;
}
