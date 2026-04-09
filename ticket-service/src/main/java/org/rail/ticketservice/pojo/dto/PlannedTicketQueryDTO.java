package org.rail.ticketservice.pojo.dto;

import lombok.Data;

@Data
public class PlannedTicketQueryDTO {

    private Long trainId;
    private String departureCode;
    private String arrivalCode;
}
