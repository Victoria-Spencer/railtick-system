package org.rail.commonapi.dto;

import lombok.Data;

import java.util.List;

@Data
public class SeatQueryDTO {

    // 列车ID
    private Long trainId;
    // 席别类型列表
    private List<Integer> seatTypes;
    // 出发站
    private String departure;
    // 到达站
    private String arrival;
}
