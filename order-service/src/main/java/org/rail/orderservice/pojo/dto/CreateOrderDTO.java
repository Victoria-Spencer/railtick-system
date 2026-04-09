package org.rail.orderservice.pojo.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CreateOrderDTO {

    public String preOrderSn;
    private String departure;
    private String arrival;
    private String departureCode;
    private String arrivalCode;
    private LocalDate ridingDate;
    private String trainId;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
}
