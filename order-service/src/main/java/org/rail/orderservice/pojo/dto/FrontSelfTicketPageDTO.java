package org.rail.orderservice.pojo.dto;

import lombok.Data;
import org.rail.commonservice.pageQuery.PageQuery;

import java.time.LocalDate;

/**
 * 前端传递过来的本人车票分页参数
 */
@Data
public class FrontSelfTicketPageDTO extends PageQuery {

    // 用户ID
    private Long userId;
    // 车票状态（0：未出行，1：已出行）
    private Integer ticketStatus;
    // 开始日期
    private LocalDate startDate;
    // 结束日期
    private LocalDate endDate;
    // 列车车次
    private String trainNumber;
}
