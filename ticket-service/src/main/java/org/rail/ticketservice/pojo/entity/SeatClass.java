package org.rail.ticketservice.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 席别实体集合
 */
@Data
public class SeatClass {

    private Long id;
    // 席别类型：0-商等座 1-一等座 2-二务座...
    private Integer type;
    private String name;
    private Integer price;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
