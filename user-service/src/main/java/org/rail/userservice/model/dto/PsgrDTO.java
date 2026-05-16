package org.rail.userservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PsgrDTO {

    @NotBlank(message = "乘车人姓名不能为空")
    private String realName;
    // 证件类型：0-身份证 1-护照 2-港澳通行证
    @NotNull(message = "证件类型不能为空")
    private Integer idType;
    @NotBlank(message = "证件号码不能为空")
    private String idCard;
    // 优惠类型，0-3 区分成人、儿童、学生、残疾军人
    @NotNull(message = "优惠类型不能为空")
    private Integer discountType;
    @NotBlank(message = "手机号不能为空")
    private String phone;
}
