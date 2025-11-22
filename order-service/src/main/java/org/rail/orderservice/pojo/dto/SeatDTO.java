package org.rail.orderservice.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatDTO {

    // 车厢号
    private String carriageNumber;
    // 座位号
    private String seatNo;
}
