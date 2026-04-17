package org.rail.ticketservice.pojo.vo;

import lombok.Data;

@Data
public class SeatClassVO {

    private Long trainId;
    private Long seatClassId;
    // 席别类型：0-商等座 1-一等座 2-二务座...
    private Integer seatType;
    private String name;
    private Integer price;
    private Integer availableSeatNum;
    private Integer totalSeatNum;
    // 席别候补标识
    private boolean candidate;
}
