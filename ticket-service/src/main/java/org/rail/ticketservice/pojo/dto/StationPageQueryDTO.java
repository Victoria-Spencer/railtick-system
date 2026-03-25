package org.rail.ticketservice.pojo.dto;

import lombok.Data;
import org.rail.common.core.pageQuery.PageQuery;

@Data
public class StationPageQueryDTO extends PageQuery {

    // 查询类型 0：热门 1：A-E 2：F-J 3：K-O 4：P-T 5：U-Z
    private Integer queryType;
    // 名称，可输入城市、站点以及拼音
    private String keyword;
}
