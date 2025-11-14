package org.rail.orderservice.pojo.dto;

import lombok.Data;
import org.rail.commonservice.pageQuery.PageQuery;

import java.time.LocalDate;

@Data
public class OrderPageQueryDTO extends PageQuery {

    // 用户ID
    private Integer userId;
    // 状态类型 0：未完成 1：未出行 2：历史订单
    private Integer orderStatus;
    // 排序条件
    private Integer orderType;
    // 开始日期
    private LocalDate startDate;
    // 结束日期
    private LocalDate endDate;
    // 订单号
    private String orderSn;
    // 列车车次
    private String trainNumber;
    // 真实姓名
    private String realName;
}
