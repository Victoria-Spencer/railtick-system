package org.rail.userservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateInfoDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;
    private String email;
    private Integer userType;
    // 邮编
    private String postCode;
    private String address;
}
