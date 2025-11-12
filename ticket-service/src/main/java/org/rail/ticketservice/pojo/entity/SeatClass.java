package org.rail.ticketservice.pojo.entity;

import java.time.LocalDateTime;

/**
 * 席别实体集合
 */
public class SeatClass {

    // 席别id
    private Long id;
    // 席别类型
    private Integer type;
    // 席别名称
    private String name;
    // 席别价格
    private Integer price;
    // 创建时间
    private LocalDateTime createTime;
    // 更新时间
    private LocalDateTime updateTime;
}
