package org.rail.api.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量新增座位区间占用
 */
@Data
public class BatchSeatIntervalInsertDTO {

    // ===================== 公共字段（所有座位相同）=====================
    private Long trainId;
    private Long orderId;
    private Integer orderType;
    private Integer status;
    private String departureCode;
    private String arrivalCode;
    private LocalDateTime expireTime;

    // ===================== 座位列表 =====================
    private List<SeatBaseDTO> seatList;
}

