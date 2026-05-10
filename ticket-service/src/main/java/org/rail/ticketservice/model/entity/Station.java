package org.rail.ticketservice.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Station {

    private Long id;
    // 查询类型（0：热门 1：A-E 2：F-J 3：K-O 4：P-T 5：U-Z）
    private Integer queryType;
    // 名称（城市、站点、以及拼音）
    private String name;
    private String code;
    private String spell;
}
