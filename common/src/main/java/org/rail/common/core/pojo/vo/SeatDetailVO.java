package org.rail.common.core.pojo.vo;

import lombok.Data;

@Data
public class SeatDetailVO {

    private Long id;
    private Long trainSeatClassId;
    private String seatNo;
    private Integer status;
    private Long trainId;
}
