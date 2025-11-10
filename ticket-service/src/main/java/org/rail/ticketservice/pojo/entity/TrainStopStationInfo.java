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
public class TrainStopStationInfo {

    // 站点id
    private Long stationId;
    // 站序
    private Integer sequence;
    // 站名名称
    private String stationName;
    // 到站时间
    private LocalDateTime arrivalTime;
    // 出发时间
    private LocalDateTime departureTime;
    // 停留时间
    private Integer stopoverTime;
}
