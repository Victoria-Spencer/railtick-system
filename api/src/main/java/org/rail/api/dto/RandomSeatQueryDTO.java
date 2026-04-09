package org.rail.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class RandomSeatQueryDTO {

    private Long trainId;
    private List<Integer> seatTypes;
    private String departureCode;
    private String arrivalCode;
}
