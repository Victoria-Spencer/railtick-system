package org.rail.orderservice.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单
 */
@Data
public class Order {

    // 订单id
    private Long id;
    // userId，归属用户（逻辑外键）
    private Long userId;
    // 列车id
    private Long trainId;
    // 订单号
    private String orderSn;
    // 关联的预订单号
    private String preOrderSn;
    // 订单状态
    private Integer status;
    // 总金额
    private Double totalAmount;
    // 创建时间
    private LocalDateTime createTime;
    // 更新时间
    private LocalDateTime updateTime;
}
