package org.rail.common.core.pageQuery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageQuery {

    protected Integer pageNumber = 1;
    protected Integer pageSize = 10;
}
