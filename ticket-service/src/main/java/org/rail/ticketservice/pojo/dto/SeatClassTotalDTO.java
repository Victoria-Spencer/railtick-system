package org.rail.ticketservice.pojo.dto;

import lombok.Data;

@Data
public class SeatClassTotalDTO {

    private Long id;    // trainSeatClassId
    private Long seatClassId;  // 席别id
    private Integer totalSeats;   // 总座位数
}
