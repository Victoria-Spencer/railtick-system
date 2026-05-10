package org.rail.ticketservice.model.dto;

import lombok.Data;

@Data
public class SequenceQueryDTO {

    private Long trainId;
    private String departureCode;
    private String arrivalCode;
}
