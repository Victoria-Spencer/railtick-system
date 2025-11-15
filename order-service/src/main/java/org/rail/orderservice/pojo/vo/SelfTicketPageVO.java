package org.rail.orderservice.pojo.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SelfTicketPageVO {

    // 订单明细ID
    private Integer id;
    // 出发站点
    private String departure;
    // 到达站点
    private String arrival;
    // 乘车日期
    private LocalDate ridingDate;
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
    // 座位号
    private String seatNo;
    // 真实姓名
    private String realName;
    // 车票类型
    private Integer ticketType;
    // 订单金额
    private Integer amount;
    // 证件类型
    private Integer idType;
    // 证件号
    private String idCard;
    // 退票状态
    private Boolean refundStatus;
}
