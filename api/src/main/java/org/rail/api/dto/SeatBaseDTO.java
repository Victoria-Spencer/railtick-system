package org.rail.api.dto;

import lombok.Data;

/**
 * 座位基础信息（仅唯一字段）
 */
@Data
public class SeatBaseDTO {
    private Integer seatType;    // 席别
    private String carriageNumber; // 车厢号
    private String seatNo;      // 座位号
}