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

    private List<String> departureCodes;
    private List<String> arrivalCodes;
    private List<Integer> trainTypeIds;
    private List<Integer> seatTypes;
    private LocalDate departureDate;
}
