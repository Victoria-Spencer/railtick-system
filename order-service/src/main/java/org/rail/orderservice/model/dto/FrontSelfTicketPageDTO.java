package org.rail.orderservice.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.rail.common.core.model.query.PageQuery;

import java.time.LocalDate;

/**
 * 前端传递过来的本人车票分页参数
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class FrontSelfTicketPageDTO extends PageQuery {

    private Long userId;
    // 车票类型（0：成人，1：儿童，2：学生，3：残疾军人）
    private Integer ticketType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String trainNumber;
}
