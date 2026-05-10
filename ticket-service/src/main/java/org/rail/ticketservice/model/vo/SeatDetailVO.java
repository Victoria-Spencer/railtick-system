package org.rail.ticketservice.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SeatDetailVO {

    protected Long id;
    protected Long trainId;
    protected Long trainSeatClassId;
    protected Integer seatType;
    protected String carriageNumber;
    protected String seatNo;
    protected Integer status;
}
