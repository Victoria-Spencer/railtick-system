package org.rail.ticketservice.pojo.entity;

import lombok.Data;

/**
 * 列车席别信息
 */
@Data
public class TrainSeatClass {

    private Long id;
    private Long trainId;
    private Integer seatClassId;
    private String carriageNumber;
    private Integer totalSeats;
}
