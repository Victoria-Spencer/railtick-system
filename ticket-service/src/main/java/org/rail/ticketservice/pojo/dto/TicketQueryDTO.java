package org.rail.ticketservice.pojo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketQueryDTO {

    @NotEmpty(message = "出发站编码列表不能为空")
    private List<String> departureCodes;

    @NotEmpty(message = "到达站编码列表不能为空")
    private List<String> arrivalCodes;

    private List<Integer> trainTypeIds;
    private List<Integer> seatTypes;

    @NotNull(message = "出发日期不能为空")
    private LocalDate departureDate;
}
