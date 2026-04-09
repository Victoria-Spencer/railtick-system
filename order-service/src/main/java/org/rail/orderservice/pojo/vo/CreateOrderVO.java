package org.rail.orderservice.pojo.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateOrderVO {

    private String orderSn;
    private List<OrderDetailsVO> createOrderDetailsVOList;
    private LocalDate ridingDate;
    private String trainNumber;
    private String departure;
    private String arrival;
    private String departureCode;
    private String arrivalCode;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
}
