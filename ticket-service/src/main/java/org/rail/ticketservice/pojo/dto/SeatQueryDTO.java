package org.rail.ticketservice.pojo.dto;

import lombok.Data;

@Data
public class SeatQueryDTO {

    // 列车ID
    private Long trainId;
    // 出发站站序
    private Integer startSequence;
    // 到达站站序
    private Integer endSequence;
}
