package org.rail.orderservice.model.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单
 */
@Data
public class Order {

    private Long id;
    private Long userId;
    private Long trainId;
    private String orderSn;
    private String preOrderSn;

    // 订单状态：0-待支付 1-已支付 2-已取消 3-部分退票 4-全部退票
    private Integer status;

    private BigDecimal totalAmount;
    private LocalDateTime payTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
