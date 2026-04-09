package org.rail.common.core.pageQuery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageQuery {

    private Integer pageNumber = 1;
    private Integer pageSize = 10;
}
