package org.rail.orderservice.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreatePreOrderDTO {

    // 列车ID
    private Long trainId;
    // 用户ID
    private Long userId;
    // 乘车人集合
    List<PassengerOrderDetailDTO> passengerOrderDetailDTOList;
    // 选择座位集合 (tempSeatNo)
    List<String> chooseSeats;
}
