package org.rail.orderservice.pojo.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateOrderVO {

    // 订单号
    private String orderSn;
    // 订单详情
    private List<CreateOrderDetailsVO> createOrderDetailsVOList;
    // 乘车日期
    private LocalDate ridingDate;
    // 列车车次
    private String trainNumber;
    // 出发站点
    private String departure;
    // 到达站点
    private String arrival;
    // 出发时间
    private LocalDateTime departureTime;
    // 到达时间
    private LocalDateTime arrivalTime;
}
