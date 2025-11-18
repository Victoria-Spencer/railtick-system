package org.rail.commonapi.dto;

import lombok.Data;

@Data
public class SeatTypeQueryDTO {

    // 列车ID
    private Long trainId;
    // 席别类型
    private Integer seatType;
    // 需要的座位数量
    private Integer requiredCount;
    // 出发站编码
    private String departureCode;
    // 到达站编码
    private String arrivalCode;
}
