package org.rail.ticketservice.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 封装列车基本信息
 */
@Data
public class TrainDetailVO {

    private Long trainId;
    private String trainNumber;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private String departure;
    private String arrival;
    private String departureCode;
    private String arrivalCode;
    private Integer daysArrived;
    private LocalDateTime saleTime;
    private boolean saleStatus;
    private Integer departureStationId;
    private Integer arrivalStationId;
    private Integer startSequence;
    private Integer endSequence;
    private List<TrainTypeVO>  trainTypeVOList;
}
