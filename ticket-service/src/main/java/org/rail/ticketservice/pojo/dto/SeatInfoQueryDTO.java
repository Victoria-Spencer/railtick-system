package org.rail.ticketservice.pojo.dto;

import lombok.Data;

@Data
public class SeatInfoQueryDTO {

    // 列车ID
    private Long trainId;
    // 列车席别类型
    private Integer seatType;
    // 车厢号
    private String carriageNumber;
    // 座位号
    private String seatNo;
}
