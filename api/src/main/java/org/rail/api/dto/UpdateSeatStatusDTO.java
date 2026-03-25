package org.rail.api.dto;

import lombok.Data;

@Data
public class UpdateSeatStatusDTO {

    // 列车id
    private Long trainId;
    // 席别类型
    private Integer seatType;
    // 车厢号
    private String carriageNumber;
    // 座位号
    private String seatNo;
}
