package org.rail.orderservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单分页-数据库查询极简DTO
 * 仅用于数据库全量查询，不承载分页、动态筛选字段
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDbQueryDTO {
    /** 用户ID */
    private Long userId;
    /** 订单状态 */
    private Integer orderStatus;
}