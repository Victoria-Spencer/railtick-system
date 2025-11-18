package org.rail.commonapi.dto;

import lombok.Data;

@Data
public class AvailableSeatDTO {

    // 席别类型
    private Integer seatType;
    // 车厢号
    private String carriageNumber;
    // 座位号
    private String seatNo;
}
