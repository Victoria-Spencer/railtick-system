package org.rail.orderservice.pojo.entity;

import lombok.Data;

/**
 * 预订单明细
 */
@Data
public class PreOrderDetails {

    // 预订单明细ID
    private Long id;
    // 预订单ID（外键）
    private Long preOrderId;
    // 乘客姓名
    private String realName;
    // 证件类型
    private Long idType;
    // 证件号码
    private String idCard;
    // 票种（学生票等）
    private Integer ticketType;
    // 席别类型
    private Integer seatType;
    // 临时座位号
    private String tempSeatNo;
    // 总金额
    private Double amount;
}
