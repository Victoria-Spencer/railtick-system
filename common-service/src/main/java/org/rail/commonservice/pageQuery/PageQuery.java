package org.rail.commonservice.pageQuery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageQuery {

    // 用户id
    private Long userId;
    // 真实姓名
    private String realName;
    private Integer page;
    private Integer size;
}
