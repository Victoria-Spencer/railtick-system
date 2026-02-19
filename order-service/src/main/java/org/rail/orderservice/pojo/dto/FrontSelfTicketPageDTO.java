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
    // 车票类型（0：成人，1：儿童，2：学生，3：残疾军人）
    private Integer ticketType;
    // 开始日期
    private LocalDate startDate;
    // 结束日期
    private LocalDate endDate;
    // 列车车次
    private String trainNumber;
}
