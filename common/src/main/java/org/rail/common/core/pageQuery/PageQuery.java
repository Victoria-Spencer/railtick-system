package org.rail.common.core.pageQuery;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageQuery {

    @Min(1)
    protected Integer pageNumber = 1;
    @Min(1)
    protected Integer pageSize = 10;
}
