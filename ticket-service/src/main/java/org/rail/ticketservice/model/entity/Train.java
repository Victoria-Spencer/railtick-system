package org.rail.ticketservice.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Train {

    private Long id;
    private String trainNumber;
//    // 列车属性（highSpeedTrainAttribute，bulletTrainAttribute，regularTrainAttribute）
//    private TrainAttributes  trainAttributes;
    private Integer daysArrived;
    private LocalDateTime saleTime;
    // 可售状态：0-未开售 1-可售 2-已停售
    private Integer saleStatus;
}
