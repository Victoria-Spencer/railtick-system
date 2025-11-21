package org.rail.commonapi.dto;

import lombok.Data;

/**
 * 新增操作
 */
@Data
public class SeatIntervalOccupyInsertDTO extends SeatIntervalOccupyOperateDTO {

    // 出发站编码
    private String departureCode;
    // 到达站编码
    private String arrivalCode;
}
