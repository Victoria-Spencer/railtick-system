package org.rail.userservice.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "密码不能为空")
    private String password;
    @NotBlank(message = "真实名称不能为空")
    private String realName;
    // 证件类型：0-身份证 1-护照 2-港澳通行证
    private Integer idType;
    @NotBlank(message = "证件号不能为空")
    private String idCard;
    @NotBlank(message = "手机号不能为空")
    private String phone;
    private String email;
}
