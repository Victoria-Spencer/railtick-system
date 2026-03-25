package org.rail.common.core.pageQuery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageQuery {

    // 页码
    private Integer pageNumber = 1;
    // 每页记录数
    private Integer pageSize = 10;
}
