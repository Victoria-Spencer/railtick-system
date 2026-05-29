package org.rail.orderservice.model.dto;

import lombok.Data;

/**
 * 对前端传递过来的参数封装后的本人车票分页参数
 */
@Data
public class SelfTicketPageDTO{

    // 证件类型：0-身份证 1-护照 2-港澳通行证
    private Integer idType;
    private String idCard;
}
