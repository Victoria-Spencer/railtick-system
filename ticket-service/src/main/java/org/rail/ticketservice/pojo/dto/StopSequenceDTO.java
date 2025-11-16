package org.rail.ticketservice.pojo.dto;

import lombok.Data;

@Data
public class StopSequenceDTO {

    // 出发站站序
    private Integer departureSequence;
    // 终点站站序
    private Integer arrivalSequence;
}
