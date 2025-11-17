package org.rail.ticketservice.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StopInfoDTO {

    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private String departure;
    private String arrival;
    private String departureCode;
    private String arrivalCode;
    private Integer departureSequence;
    private Integer arrivalSequence;
    private Integer departureStationId;
    private Integer arrivalStationId;
}
