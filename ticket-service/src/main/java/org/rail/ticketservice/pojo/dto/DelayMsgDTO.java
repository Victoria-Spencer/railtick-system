package org.rail.ticketservice.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DelayMsgDTO {
    private Long trainId;
    private Long seatId;
    private String lockId;
}