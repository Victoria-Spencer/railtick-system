package org.rail.userservice.pojo.entity;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Passenger {

    // 乘车人id
    private Long id;

    // 所属用户（逻辑外键）
    private Long userId;

    // 真实姓名
    private String realName;

    // 证件类型,0：身份证号
    private Integer idType;

    // 证件编号
    private String idCard;

    // 优惠类型
    private Integer discountType;

    // 手机号
    private String phone;

    // 审核状态
    private Integer verifyStatus;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;
}
