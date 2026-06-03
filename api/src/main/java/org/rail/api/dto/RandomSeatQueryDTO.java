package org.rail.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RandomSeatQueryDTO {

    @NotNull(message = "车次ID不能为空")
    private Long trainId;

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "状态不能为空")
    private Integer status;

    @NotNull(message = "座位锁定过期时间不能为空")
    private LocalDateTime expireTime;

    @NotNull(message = "席别类型不能为空")
    private Integer seatType;

    @NotBlank(message = "出发站编码不能为空")
    private String departureCode;

    @NotBlank(message = "到达站编码不能为空")
    private String arrivalCode;

    @NotNull(message = "乘客数量不能为空")
    private Integer passengerCount;

    // 用户偏好的座位序号（A/B/C/D/F）（非必须）
    private List<String> preferredSeatSymbols;
}
