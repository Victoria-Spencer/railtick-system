package org.rail.api.dto;

import lombok.Data;

@Data
public class SeatIntervalOccupyOperateDTO {

    // 列车ID
    private Long trainId;
    // 列车席别类型
    private Integer seatType;
    // 车厢号
    private String carriageNumber;
    // 座位号
    private String seatNo;
    // ID（正式订单或预订单）
    private Long orderId;
    // 订单类型（1：预订单，2：正式订单）
    private Integer orderType;
    // 占用状态
    private Integer status;
}
