package org.rail.ticketservice.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlannedTicketQueryDTO {

    @NotNull(message = "车次ID不能为空")
    private Long trainId;

    @NotBlank(message = "出发站编码不能为空")
    private String departureCode;

    @NotBlank(message = "到达站编码不能为空")
    private String arrivalCode;
}
