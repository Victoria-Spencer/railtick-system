package org.rail.userservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.rail.common.core.model.query.PageQuery;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PsgrPageQueryDTO extends PageQuery {

    private Long userId;
    private String realName;
    // 审核状态
    private Integer verifyStatus;
    // 优惠类型，0-3 区分成人、儿童、学生、残疾军人
    private Integer discountType;
    private String sortField;
    // 排序方式(0：asc，1：desc)
    private Integer sortOrder;
}
