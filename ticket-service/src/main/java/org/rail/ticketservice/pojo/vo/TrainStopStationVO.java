package org.rail.ticketservice.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainStopStationVO {

    // 站序
    private Integer sequence;
    // 站名名称
    private String stationName;
    // 到站时间
    private LocalDateTime arrivalTime;
    // 出发时间
    private LocalDateTime departureTime;
    // 停留时间
    private Long stopoverTime;
}
