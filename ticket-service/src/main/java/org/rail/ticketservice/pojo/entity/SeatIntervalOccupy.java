package org.rail.ticketservice.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SeatIntervalOccupy {

    private Long id;
    private Long seatId;
    private Integer startSequence;
    private Integer endSequence;
    // ID（预订单或正式订单）
    private Long orderId;
    // 订单类型（0：预订单，1：正式订单）
    private Integer orderType;
    // 占用状态（0：锁定中，1：已售出，2：已释放）
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
    private String lockId;

    // 仅用于接收关联查询出的 train_id，数据库表不存在
    private Long trainId;
}
