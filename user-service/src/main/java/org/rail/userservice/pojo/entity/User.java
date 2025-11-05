package org.rail.userservice.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {


    // 用户id
    private Long id;

    // 用户名
    private String username;

    // 真实名称
    private String realName;

    // 密码
    private String password;

    // 证件类型,0：身份证号
    private Integer idType;

    // 证件号
    private String idCard;

    // 手机号
    private String phone;

    // 邮箱
    private String email;

    // 旅客类型
    private Integer userType;

    // 邮编
    private String postCode;

    // 地址
    private String address;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;
}
