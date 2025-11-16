package org.rail.commonapi.dto;

import lombok.Data;

@Data
public class UserIdCardDTO {

    // 证件类型
    private Integer idType;
    // 证件号码
    private String idCard;
}
