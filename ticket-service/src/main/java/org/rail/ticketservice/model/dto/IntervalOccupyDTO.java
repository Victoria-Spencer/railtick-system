package org.rail.ticketservice.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class IntervalOccupyDTO {

    private Long trainId;
    // 席别类型：0-商等座 1-一等座 2-二务座...
    private Integer seatType;
    private String carriageNumber;
    private String seatNo;
    private List<SequenceDTO> intervalList;
}
