package org.rail.ticketservice.pojo.dto;

import lombok.Data;

@Data
public class PlannedTicketQueryDTO {

    // 列车id
    private Long id;
    // 出发车站
    private String departure;
    // 到达车站
    private String arrival;
}
