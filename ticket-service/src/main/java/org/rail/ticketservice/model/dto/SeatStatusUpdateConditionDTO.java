package org.rail.ticketservice.model.dto;

import lombok.Data;

@Data
public class SeatStatusUpdateConditionDTO {

    private Long trainId;
    // 席别类型：0-商等座 1-一等座 2-二务座...
    private Integer seatType;
    private String carriageNumber;
    private String seatNo;
    // 占用状态：0-完全可选 1-部分占用 2-全区间已售
    private Integer status;
}
