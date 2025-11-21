package org.rail.ticketservice.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SeatIntervalOccupy {

    // ID
    private Long id;
    // 座位ID
    private Long seatId;
    // 出发站序
    private Integer startSequence;
    // 到达站序
    private Integer endSequence;
    // ID（预订单或正式订单）
    private Long orderId;
    // 订单类型（1：预订单，2：正式订单）
    private Integer orderType;
    // 占用状态（1：锁定中，2：已售出，3：已释放）
    private Integer status;
    // 创建时间
    private LocalDateTime createTime;
    // 过期时间
    private LocalDateTime expireTime;
}
