package org.rail.ticketservice.pojo.dto;

import lombok.Data;

@Data
public class SeatClassDTO {

    /**
     * 席别类型（0：商务座，1：一等座，2：二等座等）
     */
    private Integer type;

    /**
     * 席别名称（如“商务座”“一等座”）
     */
    private String name;

    /**
     * 席别价格（精确到小数点后两位）
     */
    private Integer price;
}
