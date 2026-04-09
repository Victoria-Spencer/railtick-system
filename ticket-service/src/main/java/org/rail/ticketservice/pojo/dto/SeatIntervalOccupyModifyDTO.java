package org.rail.ticketservice.pojo.dto;

import lombok.Data;

@Data
public class SeatIntervalOccupyModifyDTO {

    private Long orderId;
    // 订单类型 0：预订单 1：正式订单
    private Integer orderType;
    // 占用状态 0：预订单锁定 1：正式订单已售 2：已释放/已取消
    private Integer status;
}
