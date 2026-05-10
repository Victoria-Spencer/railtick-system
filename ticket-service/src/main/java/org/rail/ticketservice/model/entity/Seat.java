package org.rail.ticketservice.model.entity;

import lombok.Data;

/**
 * 座位
 */
@Data
public class Seat {

    private Long id;
    private Long trainSeatClassId;
    private String seatNo;
    // 状态：0-完全可选 1-部分区间占用 2-全区间已售
    private Integer status;
}
