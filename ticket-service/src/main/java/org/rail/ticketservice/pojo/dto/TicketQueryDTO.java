package org.rail.ticketservice.pojo.dto;

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

    // 出发车站编码
    private List<String> departureCodes;
    // 到达车站编码
    private List<String> arrivalCodes;
    // 列车类型
    private List<Integer> trainTypeIds;
    // 席别类型
    private List<Integer> seatTypes;
    // 出发日
    private LocalDate departureDate;
}
