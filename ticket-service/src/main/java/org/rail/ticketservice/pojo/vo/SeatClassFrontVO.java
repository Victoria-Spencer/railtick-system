package org.rail.ticketservice.pojo.vo;

import lombok.Data;

@Data
public class SeatClassFrontVO {

    // 席别类型
    private Long seatClassId;
    // 席别类型
    private Integer seatType;
    // 席别名称（商务座等）
    private String name;
    // 席别价格
    private Integer price;
    // 可用座位数量
    private Integer availableSeatNum;
    // 总数量
    private Integer totalSeatNum;
    // 席别候补标识
    private boolean candidate;
}
