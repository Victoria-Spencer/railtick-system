package org.rail.ticketservice.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 列车经停站信息
 */
@Data
public class TrainStopStation {

    private Long id;
    private Long trainId;
    private Long stationId;
    private Integer sequence;
    private String stationName;
    private LocalDateTime arrivalTime;
    private LocalDateTime departureTime;
    private Long stopoverTime;
}
