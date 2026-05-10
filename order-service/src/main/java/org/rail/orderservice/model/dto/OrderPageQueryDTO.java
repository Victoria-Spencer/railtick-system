package org.rail.orderservice.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.rail.common.core.model.query.PageQuery;

import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Data
public class OrderPageQueryDTO extends PageQuery {

    private Integer userId;
    // 状态类型 0：未完成 1：未出行 2：历史订单
    private Integer orderStatus;
    // 排序条件（乘车日期或订票时间）
    private Integer orderType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String orderSn;
    private String trainNumber;
    private String realName;
}
