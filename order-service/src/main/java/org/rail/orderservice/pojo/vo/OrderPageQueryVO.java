package org.rail.orderservice.pojo.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class OrderPageQueryVO {

    // 出发站点
    private String departure;
    // 到达站点
    private String arrival;
    // 出行日期
    private LocalDate ridingDate;
    // 列车车次
    private String trainNumber;
    // 出发时间
    private LocalDateTime departureTime;
    // 到达时间
    private LocalDateTime arrivalTime;
    // 订单状态（0-待支付，1-已支付，2-已取消，3-部分退票，4-全部退票）
    private Integer status;
    // 订票日期
    private LocalDate orderDate;
    // 乘车人订单详情
    private OrderDetailsVO orderDetailsVO;
}
