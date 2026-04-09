package org.rail.ticketservice.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 列车经停站信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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
