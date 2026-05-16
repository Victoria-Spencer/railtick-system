package org.rail.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量新增座位区间占用
 */
@Data
public class BatchSeatIntervalInsertDTO {

    // ===================== 公共字段（所有座位相同）=====================
    @NotNull(message = "车次ID不能为空")
    private Long trainId;

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "订单类型不能为空")
    private Integer orderType;

    @NotNull(message = "状态不能为空")
    private Integer status;

    @NotBlank(message = "出发站编码不能为空")
    private String departureCode;

    @NotBlank(message = "到达站编码不能为空")
    private String arrivalCode;
    private LocalDateTime expireTime;

    // ===================== 座位列表 =====================
    @NotEmpty(message = "座位列表不能为空")
    private List<SeatBaseDTO> seatList;
}

