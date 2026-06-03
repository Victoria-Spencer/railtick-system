package org.rail.ticketservice.model.entity;

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
    // 占用状态（0：预定单临时锁定，1：正式订单临时锁定，2：已售出，3：已释放）
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
    private Long lockId;

    // 仅用于接收关联查询出的 train_id，数据库表不存在
    private Long trainId;
}
