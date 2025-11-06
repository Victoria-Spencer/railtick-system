package org.rail.userservice.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDTO {

    // 用户名
    @NotBlank(message = "用户名不能为空")
    private String username;
    // 密码
    @NotBlank(message = "密码不能为空")
    private String password;
    // 真实名称
    @NotBlank(message = "真实名称不能为空")
    private String realName;
    // 证件类型,0：身份证号
    private Integer idType;
    // 证件号
    @NotBlank(message = "证件号不能为空")
    private String idCard;
    // 手机号
    @NotBlank(message = "手机号不能为空")
    private String phone;
    // 邮箱
    private String email;
}
