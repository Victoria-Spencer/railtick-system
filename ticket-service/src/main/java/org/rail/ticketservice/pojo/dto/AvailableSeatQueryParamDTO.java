package org.rail.ticketservice.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class AvailableSeatQueryParamDTO {

    // 列车ID
    private Long trainId;
    // 席别类型列表
    private List<Integer> seatTypes;
    // 出发站序
    private Integer departureSequence;
    // 到达站序
    private Integer arrivalSequence;
}
