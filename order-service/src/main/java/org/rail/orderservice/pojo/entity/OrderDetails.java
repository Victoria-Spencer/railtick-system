package org.rail.orderservice.pojo.entity;

import lombok.Data;
import org.rail.orderservice.constant.TicketType;

import java.time.LocalDateTime;

/**
 * 订单明细
 */
@Data
public class OrderDetails {

    // 订单明细id
    private Long id;
    // 订单id（逻辑外键）
    private Long orderId;
    // 关联的预订单明细
    private Long preOrderDetailId;
    // 出发站点
    private String departure;
    // 到达站点
    private String arrival;
    // 出发站编码
    private String departureCode;
    // 到达站编码
    private String arrivalCode;
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
    // 座位号
    private String seatNo;
    // 真实姓名
    private String realName;
    // 证件类型
    private Integer idType;
    // 证件号码
    private String idCard;
    // 车票类型（学生票、成人票等）
    private Integer ticketType;
    // 订单金额
    private Double amount;
    // 退票状态
    private Integer refundStatus;
}
