package org.rail.api.dto;

import lombok.Data;

/**
 * 座位基础信息（仅唯一字段）
 */
@Data
public class SeatBaseDTO {
    private Integer seatType;
    private String carriageNumber;
    private String seatNo;
}