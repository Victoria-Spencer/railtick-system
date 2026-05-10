package org.rail.orderservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CreateOrderDTO {

    @NotBlank(message = "预订单号不能为空")
    private String preOrderSn;

    @NotBlank(message = "出发站名称不能为空")
    private String departure;

    @NotBlank(message = "到达站名称不能为空")
    private String arrival;

    @NotBlank(message = "出发站编码不能为空")
    private String departureCode;

    @NotBlank(message = "到达站编码不能为空")
    private String arrivalCode;

    @NotNull(message = "乘车日期不能为空")
    private LocalDate ridingDate;

    @NotBlank(message = "车次ID不能为空")
    private String trainId;

    @NotNull(message = "出发时间不能为空")
    private LocalDateTime departureTime;

    @NotNull(message = "到达时间不能为空")
    private LocalDateTime arrivalTime;

    /*@NotBlank(message = "防重提交令牌不能为空")
    private String submitToken;*/
}
