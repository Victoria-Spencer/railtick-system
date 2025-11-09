package org.rail.userservice.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rail.commonservice.pageQuery.PageQuery;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PsgrPageQueryDTO extends PageQuery {

    // 审核状态
    private Integer verifyStatus;
    // 优惠类型，区分成人、儿童、学生
    private Integer discountType;
    // 排序字段
    private String sortField;
    // 排序方式(0：asc，1：desc)
    private Integer sortOrder;
}
