package org.rail.commonapi.dto;

import lombok.Data;

import java.util.List;

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
