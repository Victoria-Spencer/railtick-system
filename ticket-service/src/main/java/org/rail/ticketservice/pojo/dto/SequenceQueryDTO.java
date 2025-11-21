package org.rail.ticketservice.pojo.dto;

import lombok.Data;

@Data
public class SequenceQueryDTO {

    // 列车id
    private Long trainId;
    // 出发站站点编码
    private String departureCode;
    // 到达站站点编码
    private String arrivalCode;
}
