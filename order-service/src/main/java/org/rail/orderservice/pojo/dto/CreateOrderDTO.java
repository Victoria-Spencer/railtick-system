package org.rail.orderservice.pojo.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CreateOrderDTO {

    // 预订单号（唯一标识）
    public String preOrderSn;
    // 出发站点
    private String departure;
    // 到达站点
    private String arrival;
    // 出发站编码
    private String departureCode;
    // 到达站编码
    private String arrivalCode;
    // 乘车日期
    private LocalDate ridingDate;
    // 列车车次
    private String trainNumber;
    // 出发时间
    private LocalDateTime departureTime;
    // 到达时间
    private LocalDateTime arrivalTime;
}
