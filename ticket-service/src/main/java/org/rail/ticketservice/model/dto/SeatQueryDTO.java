package org.rail.ticketservice.model.dto;

import lombok.Data;

@Data
public class SeatQueryDTO {

    private Long trainId;
    private Integer startSequence;
    private Integer endSequence;
}
