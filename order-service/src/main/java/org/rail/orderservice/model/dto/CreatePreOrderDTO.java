package org.rail.orderservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreatePreOrderDTO {

    @NotNull(message = "列车ID不能为空")
    private Long trainId;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "出发站编码不能为空")
    private String departureCode;

    @NotBlank(message = "到达站编码不能为空")
    private String arrivalCode;

    @NotEmpty(message = "乘客信息列表不能为空")
    List<PassengerOrderDetailDTO> passengerOrderDetailDTOList;

    // 座位偏好（可选：A/B/C/D/F）
    List<String> preferredSeatSymbols;
}
