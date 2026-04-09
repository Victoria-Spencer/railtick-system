package org.rail.api.dto;

import lombok.Data;

@Data
public class SeatTypeQueryDTO {

    private Long trainId;
    // 席别类型：0-商等座 1-一等座 2-二务座...
    private Integer seatType;
    private Integer requiredCount;
    private String departureCode;
    private String arrivalCode;
}
