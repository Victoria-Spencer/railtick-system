package org.rail.userservice.pojo.entity;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Passenger {

    private Long id;
    private Long userId;
    private String realName;
    // 证件类型：0-身份证 1-护照 2-港澳通行证
    private Integer idType;
    private String idCard;
    // 优惠类型，0-3 区分成人、儿童、学生、残疾军人
    private Integer discountType;
    private String phone;
    // 审核状态：0-待审核 1-审核通过 2-审核不通过
    private Integer verifyStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
