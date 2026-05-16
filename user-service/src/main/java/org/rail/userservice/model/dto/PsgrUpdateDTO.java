package org.rail.userservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PsgrUpdateDTO {

    @NotNull(message = "乘客ID不能为空")
    private Long id;
    @NotBlank(message = "手机号不能为空")
    private String phone;
}
