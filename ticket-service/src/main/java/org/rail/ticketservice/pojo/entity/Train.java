package org.rail.ticketservice.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rail.ticketservice.enums.TrainTag;
import org.rail.ticketservice.enums.TrainType;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Train {

    // 列车id
    private Long id;

    // 车次
    private String trainNumber;

    // 列车类型
    private TrainType trainType;

    // 列车属性（highSpeedTrainAttribute，bulletTrainAttribute，regularTrainAttribute）
    private TrainAttributes  trainAttributes;

    // 列车经停站信息
    private TrainStopStationInfo trainStopStationInfo;

    // 跨天数量
    private Integer daysArrived;

    // 可售时间
    private Integer saleTime;

    // 销售状态
    private Integer saleStatus;

    // 列车标签集合（0：复兴号 1：智能动车组 2：静音车厢 3：支持选铺）
    private List<TrainTag> trainTags;

    // 席别实体集合
    private List<SeatClass> seatClassList;
}
