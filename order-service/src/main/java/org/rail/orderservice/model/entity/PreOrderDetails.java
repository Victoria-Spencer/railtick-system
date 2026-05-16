package org.rail.orderservice.model.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 预订单明细
 */
@Data
public class PreOrderDetails {

    private Long id;
    private Long preOrderId;
    private String realName;
    // 证件类型：0-身份证 1-护照 2-港澳通行证
    private Integer idType;
    private String idCard;
    // 车票类型：0-成人票 1-儿童票 2-学生票 3-残疾军人
    private Integer ticketType;
    // 席别类型：0-商等座 1-一等座 2-二务座...
    private Integer seatType;
    private String carriageNumber;
    private String tempSeatNo;
    private BigDecimal amount;
}
