package org.rail.orderservice.pojo.vo;

import lombok.Data;

@Data
public class OrderDetailsVO {

    // 订单明细ID
    private Integer id;
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
    // 票种
    private Integer ticketType;
    // 票价
    private Double amount;
    // 退票状态
    private Boolean refundStatus;
}
