package org.rail.ticketservice.pojo.entity;

import lombok.Data;

@Data
public class Station {

    private Long id;
    // 查询类型（0：热门 1：A-E 2：F-J 3：K-O 4：P-T 5：U-Z）
    private Integer queryType;
    // 名称（城市、站点、以及拼音）
    private String name;
    private String code;
    private String spell;
}
