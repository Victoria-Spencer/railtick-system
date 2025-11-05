package org.rail.ticketservice.pojo.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 列车经停站信息
 */
public class TrainStopStationInfo {

    // 站点id
    private Long stationId;
    // 站序
    private Integer sequence;
    // 站名
    private String departure;
    // 到站时间
    private LocalDateTime arrivalTime;
    // 出发时间
    private LocalDateTime departureTime;
    // 停留时间
    private LocalTime stopoverTime;
}
