package org.rail.userservice.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginDTO {

    // 用户名/邮箱/手机号码
    @NotBlank(message = "用户名/邮箱/手机号不能为空")
    private String usernameOrMailOrPhone;
    @NotBlank(message = "密码不能为空")
    private String password;
}
