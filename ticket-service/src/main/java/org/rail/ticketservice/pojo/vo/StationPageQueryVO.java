package org.rail.ticketservice.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StationPageQueryVO {

    // 站点名称
    private String name;
    // 站点编码
    private String code;
    // 站点拼音
    private String spell;
}
