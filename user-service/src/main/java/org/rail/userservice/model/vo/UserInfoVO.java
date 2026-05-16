package org.rail.userservice.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO {


    private Long id;
    private String username;
    private String realName;
    // 证件类型：0-身份证 1-护照 2-港澳通行证
    private Integer idType;
    private String idCard;
    private String phone;
    private String email;
    private Integer userType;
    private String postCode;
    private String address;
}
