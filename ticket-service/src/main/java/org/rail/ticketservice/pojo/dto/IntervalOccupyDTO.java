package org.rail.ticketservice.pojo.dto;

import cn.hutool.core.lang.Pair;
import lombok.Data;

import java.util.List;

@Data
public class IntervalOccupyDTO {

    // 列车类型
    private Long trainId;
    // 席别类型
    private Integer seatType;
    // 车厢号
    private String carriageNumber;
    // 座位号
    private String seatNo;
    // 占用区间
    private List<Pair<Integer, Integer>> intervalList;
}
