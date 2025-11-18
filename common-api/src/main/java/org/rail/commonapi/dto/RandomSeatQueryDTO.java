package org.rail.commonapi.dto;

import lombok.Data;

import java.util.List;

@Data
public class RandomSeatQueryDTO {

    // 列车ID
    private Long trainId;
    // 席别类型列表
    private List<Integer> seatTypes;
    // 出发站编码
    private String departureCode;
    // 到达站编码
    private String arrivalCode;
}
