package org.rail.userservice.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateInfoDTO {

    // 用户名
    @NotBlank(message = "用户名不能为空")
    private String username;
    // 邮箱
    private String email;
    // 旅客类型
    private Integer userType;
    // 邮编
    private String postCode;
    // 地址
    private String address;
}
