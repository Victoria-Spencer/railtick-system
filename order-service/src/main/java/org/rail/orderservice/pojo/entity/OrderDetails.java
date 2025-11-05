package org.rail.orderservice.pojo.entity;

import org.rail.orderservice.enums.TicketType;

import java.time.LocalDateTime;

public class OrderDetails {

    // 订单明细id
    private Long id;
    // 订单id（逻辑外键）
    private Long orderId;
    // 出发站点
    private String departure;
    // 到达站点
    private String arrival;
    // 乘车日期
    private LocalDateTime ridingDate;
    // 列车车次
    private String trainNumber;
    // 出发时间
    private LocalDateTime departureTime;
    // 到达时间
    private LocalDateTime arrivalTime;
    // 席别类型
    private Integer seatType;
    // 车厢号
    private String carriageNumber;
    // 真实姓名
    private String realName;
    // 车票类型
    private TicketType ticketType;
    // 订单金额
    private Integer amount;
}
