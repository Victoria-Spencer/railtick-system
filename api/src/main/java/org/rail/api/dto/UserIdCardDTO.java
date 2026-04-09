package org.rail.api.dto;

import lombok.Data;

@Data
public class UserIdCardDTO {

    // 证件类型：0-身份证 1-护照 2-港澳通行证
    private Integer idType;
    private String idCard;
}
