package org.rail.orderservice.pojo.dto;

import lombok.Data;
import org.rail.commonservice.pageQuery.PageQuery;

import java.time.LocalDate;

@Data
public class SelfTicketPageDTO extends PageQuery {

    // 用户ID
    private Long userId;
    // 当前用户的真实姓名
    private String realName;
    // 车票状态（0：未出行，1：已出行）
    private Integer ticketStatus;
    // 开始日期
    private LocalDate startDate;
    // 结束日期
    private LocalDate endDate;
    // 列车车次
    private String trainNumber;
}
