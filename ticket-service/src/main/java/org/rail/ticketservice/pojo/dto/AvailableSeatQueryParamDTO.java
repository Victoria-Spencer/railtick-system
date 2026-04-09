package org.rail.ticketservice.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class AvailableSeatQueryParamDTO {

    private Long trainId;
    private List<Integer> seatTypes;
    private Integer departureSequence;
    private Integer arrivalSequence;
}
