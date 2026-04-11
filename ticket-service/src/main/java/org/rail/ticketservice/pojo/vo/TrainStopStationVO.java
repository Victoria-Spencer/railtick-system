package org.rail.ticketservice.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainStopStationVO {

    private Integer sequence;
    private String stationName;
    private LocalDateTime arrivalTime;
    private LocalDateTime departureTime;
    private Long stopoverTime;
}
