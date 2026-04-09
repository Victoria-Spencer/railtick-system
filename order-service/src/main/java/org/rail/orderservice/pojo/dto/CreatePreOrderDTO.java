package org.rail.orderservice.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreatePreOrderDTO {

    private Long trainId;
    private Long userId;
    private String departureCode;
    private String arrivalCode;
    List<PassengerOrderDetailDTO> passengerOrderDetailDTOList;
    List<ChooseSeatDTO> chooseSeats;
}
