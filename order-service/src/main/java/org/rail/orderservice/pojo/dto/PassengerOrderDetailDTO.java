package org.rail.orderservice.pojo.dto;

import lombok.Data;

@Data
public class PassengerOrderDetailDTO {

    // 乘车人姓名
    private String realName;
    // 证件类型
    private Integer idType;
    // 证件号码
    private String idCard;
    // 车票类型
    private Integer ticketType;
    // 席别类型（0：商务座，1：一等座...）
    private Integer seatType;
    // 金额
    private Double amount;
}
