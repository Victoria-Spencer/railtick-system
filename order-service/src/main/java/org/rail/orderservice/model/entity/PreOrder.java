package org.rail.orderservice.model.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 *  预订单
 */
@Data
public class PreOrder {

    private Long id;
    private String preOrderSn;
    private Long userId;
    private Long trainId;
    private BigDecimal totalAmount;
    private LocalDateTime expireTime;
    // 状态：0-有效 1-已过期 2-已转为正式订单
    private Integer status;
    private LocalDateTime createTime;
}
