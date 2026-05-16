package org.rail.api.dto;

import lombok.Data;

@Data
public class PassengerRemoteDTO {
    private Long id;
    private String realName;
    private Integer idType;
    // 真实身份证号（未脱敏）
    private String idCard;
}