package org.rail.orderservice.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 *  预订单
 */
@Data
public class PreOrder {

    // 预订单ID
    private Long id;
    // 预订单号（唯一标识）
    private String preOrderSn;
    // 用户ID
    private Long userId;
    // 列车ID
    private Long trainId;
    // 总金额
    private Double totalAmount;
    // 过期时间
    private LocalDateTime expireTime;
    // 预订单状态
    private Integer status;
    // 创建时间
    private LocalDateTime createTime;
}
