package org.rail.orderservice.pojo.dto;

import lombok.Data;

@Data
public class ChooseSeatDTO {

    // 车厢号
    private String carriageNumber;
    // 临时座位号
    private String tempSeatNo;
}
