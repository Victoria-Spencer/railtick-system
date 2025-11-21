package org.rail.ticketservice.pojo.dto;

import lombok.Data;

@Data
public class SeatIntervalOccupyModifyDTO {

    // ID（预订单或正式订单）
    private Long orderId;
    // 订单类型（1：预订单，2：正式订单
    private Integer orderType;
    // 占用状态
    private Integer status;
}
