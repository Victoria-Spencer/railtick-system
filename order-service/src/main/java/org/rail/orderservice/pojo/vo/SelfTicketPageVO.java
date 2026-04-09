package org.rail.orderservice.pojo.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SelfTicketPageVO {

    private Integer id;
    private String departure;
    private String arrival;
    private String departureCode;
    private String arrivalCode;
    private LocalDate ridingDate;
    private String trainNumber;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    // 席别类型：0-商等座 1-一等座 2-二务座...
    private Integer seatType;
    private String carriageNumber;
    private String seatNo;
    private String realName;
    private Integer ticketType;
    private Integer amount;
    // 证件类型：0-身份证 1-护照 2-港澳通行证
    private Integer idType;
    private String idCard;
    // 退票状态：0-未退票 1已退票
    private Boolean refundStatus;
}
