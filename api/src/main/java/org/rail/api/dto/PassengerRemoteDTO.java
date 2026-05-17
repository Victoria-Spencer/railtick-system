package org.rail.api.dto;

import lombok.Data;

@Data
public class PassengerRemoteDTO {
    private Long id;
    private String realName;
    private Integer idType;
    private String idCard;
}