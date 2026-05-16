package org.rail.orderservice.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PassengerOrderDetailDTO {

    @NotNull(message = "乘客ID不能为空")
    private Long id;
    // 车票类型：0-成人票 1-儿童票 2-学生票 3-残疾军人
    private Integer ticketType;
    // 席别类型：0-商等座 1-一等座 2-二务座...
    private Integer seatType;
    private BigDecimal amount;
}
