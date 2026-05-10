package org.rail.orderservice.model.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderPageQueryVO {

    private String orderSn;
    private String departure;
    private String arrival;
    private String departureCode;
    private String arrivalCode;
    private LocalDate ridingDate;
    private String trainNumber;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    // 订单状态（0-待支付，1-已支付，2-已取消，3-部分退票，4-全部退票）
    private Integer status;
    private LocalDate orderDate;
    private List<OrderDetailsVO> orderDetailsVOList;
}
