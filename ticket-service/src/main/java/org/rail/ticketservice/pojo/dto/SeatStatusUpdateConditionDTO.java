package org.rail.ticketservice.pojo.dto;

import lombok.Data;

@Data
public class SeatStatusUpdateConditionDTO {

    // 列车ID
    private Long trainId;
    // 席别类型
    private Integer seatType;
    // 车厢号
    private String carriageNumber;
    // 座位号
    private String seatNo;
    // 座位状态
    private Integer status;
}
