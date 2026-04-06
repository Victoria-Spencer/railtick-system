package org.rail.api.dto;

import lombok.Data;

/**
 * 座位区间占用 新增/更新 通用DTO
 */
@Data
public class SeatIntervalOccupyDTO {

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

    // === 仅新增需要的字段（更新时传null即可） ===
    // 出发站编码
    private String departureCode;
    // 到达站编码
    private String arrivalCode;
}
