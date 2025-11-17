package org.rail.ticketservice.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 封装列车的 “经停时间列表” 及 “出发站id/到达站id（stationId)”
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrainDetailVO {

    private Long trainId;   // 列车ID
    private String trainNumber;   // 列车车次
    private Integer trainType;  // 列车类型
    private LocalDateTime departureTime;  // 出发站出发时间
    private LocalDateTime arrivalTime;    // 到达站到达时间
    private String departure;   // 出发站名称
    private String arrival;     // 到达站名称
    private String departureCode;   // 出发站编码
    private String arrivalCode;     // 到达站编码
    private Integer daysArrived;  // 到达天数
    private LocalDateTime saleTime;   // 可售时间
    private boolean saleStatus;    // 可售状态
    private Integer departureStationId;  // 出发站id
    private Integer arrivalStationId;   // 到达站id
    private Integer startSequence;  // 出发站站序
    private Integer endSequence;   // 到达站站序
}
