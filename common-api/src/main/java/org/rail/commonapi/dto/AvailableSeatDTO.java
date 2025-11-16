package org.rail.commonapi.dto;

import lombok.Data;

@Data
public class AvailableSeatDTO {

    // 车厢号
    private String carriageNumber;
    // 座位号
    private String seatNo;
}
