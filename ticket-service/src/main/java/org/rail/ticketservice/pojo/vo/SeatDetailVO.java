package org.rail.ticketservice.pojo.vo;

import lombok.Data;

@Data
public class SeatDetailVO {

    private Long id;
    private Long trainSeatClassId;
    private String carriageNumber;
    private String seatNo;
    private Integer status;
    private Long trainId;
}
