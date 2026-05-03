package org.rail.orderservice.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderDetailsVO {

    private Integer id;
    private Integer seatType;
    private String carriageNumber;
    private String seatNo;
    private String realName;
    // 证件类型：0-身份证 1-护照 2-港澳通行证
    private Integer idType;
    private String idCard;
    // 车票类型：0-成人票 1-儿童票 2-学生票 3-残疾军人
    private Integer ticketType;
    private BigDecimal amount;
    // 退票状态：0-未退票 1已退票
    private Boolean refundStatus;
}
