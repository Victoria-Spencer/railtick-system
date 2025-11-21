package org.rail.ticketservice.pojo.dto;

import lombok.Data;

@Data
public class SeatIntervalBaseDTO {

    // 座位ID
    private Long seatId;
    // 出发站序
    private Integer startSequence;
    // 到达站序
    private Integer endSequence;
}
