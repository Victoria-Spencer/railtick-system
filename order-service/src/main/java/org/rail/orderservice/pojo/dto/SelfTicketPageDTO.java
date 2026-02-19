package org.rail.orderservice.pojo.dto;

import lombok.Data;
import org.rail.commonservice.pageQuery.PageQuery;

import java.time.LocalDate;

/**
 * 对前端传递过来的参数封装后的本人车票分页参数
 */
@Data
public class SelfTicketPageDTO extends PageQuery {

    // 车票类型（0：成人，1：儿童，2：学生，3：残疾军人）
    private Integer ticketType;
    // 开始日期
    private LocalDate startDate;
    // 结束日期
    private LocalDate endDate;
    // 列车车次
    private String trainNumber;
    // 证件类型
    private Integer idType;
    // 证件号码
    private String idCard;
}
