package org.rail.orderservice.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单明细
 */
@Data
public class OrderDetails {

    private Long id;
    private Long orderId;
    private Long preOrderDetailId;
    private String departure;
    private String arrival;
    private String departureCode;
    private String arrivalCode;
    private LocalDateTime ridingDate;
    private String trainId;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    // 席别类型：0-商等座 1-一等座 2-二务座...
    private Integer seatType;
    private String carriageNumber;
    private String seatNo;
    private String realName;
    // 证件类型：0-身份证 1-护照 2-港澳通行证
    private Integer idType;
    private String idCard;
    // 车票类型：0-成人票 1-儿童票 2-学生票 3-残疾军人
    private Integer ticketType;
    private Double amount;
    // 退票状态：0-未退票 1已退票
    private Integer refundStatus;
}
