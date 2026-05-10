package org.rail.orderservice.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.rail.common.core.model.query.PageQuery;

import java.time.LocalDate;

/**
 * 对前端传递过来的参数封装后的本人车票分页参数
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SelfTicketPageDTO extends PageQuery {

    // 车票类型（0：成人，1：儿童，2：学生，3：残疾军人）
    private Integer ticketType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String trainNumber;
    // 证件类型：0-身份证 1-护照 2-港澳通行证
    private Integer idType;
    private String idCard;
}
