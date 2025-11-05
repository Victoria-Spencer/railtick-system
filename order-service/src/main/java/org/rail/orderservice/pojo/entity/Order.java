package org.rail.orderservice.pojo.entity;

import java.time.LocalDateTime;

/**
 * 订单
 */
public class Order {

    // 订单id
    private Long id;
    // userId，归属用户（逻辑外键）
    private Long userId;
    // 订单号
    private String orderSn;
    // 订单状态
    private Integer status;
    // 总金额
    private Double totalAmount;
    // 创建时间
    private LocalDateTime createTime;
    // 更新时间
    private LocalDateTime updateTime;
}
