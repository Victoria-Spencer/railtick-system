package org.rail.orderservice.pojo.entity;

import lombok.Data;

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
    private Double totalAmount;
    private LocalDateTime expireTime;
    // 状态：0-有效 1-已过期 2-已转为正式订单
    private Integer status;
    private LocalDateTime createTime;
}
