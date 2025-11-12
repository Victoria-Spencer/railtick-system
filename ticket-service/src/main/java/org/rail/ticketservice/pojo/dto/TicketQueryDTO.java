package org.rail.ticketservice.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketQueryDTO {

    // 出发地
    private String fromStation;
    // 目的地
    private String toStation;
    // 出发日
    private LocalDate departureDate;
    // 出发车站
    private String departure;
    // 到达车站
    private String arrival;
}
