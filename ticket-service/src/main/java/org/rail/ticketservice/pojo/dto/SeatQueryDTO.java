package org.rail.ticketservice.pojo.dto;

import lombok.Data;

@Data
public class SeatQueryDTO {

    private Long trainId;
    private Integer startSequence;
    private Integer endSequence;
}
