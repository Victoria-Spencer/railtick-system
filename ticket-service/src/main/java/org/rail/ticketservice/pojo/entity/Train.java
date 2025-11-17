package org.rail.ticketservice.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class Train {

    // 列车id
    private Long id;

    // 车次
    private String trainNumber;

    // 列车类型
    private Integer trainType;

//    // 列车属性（highSpeedTrainAttribute，bulletTrainAttribute，regularTrainAttribute）
//    private TrainAttributes  trainAttributes;

    // 跨天数量
    private Integer daysArrived;

    // 可售时间
    private LocalDateTime saleTime;

    // 可售状态
    private Integer saleStatus;

//    // 列车标签集合（0：复兴号 1：智能动车组 2：静音车厢 3：支持选铺）
//    private List<Integer> trainTags;
}
