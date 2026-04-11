package org.rail.ticketservice.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rail.ticketservice.pojo.entity.Train;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketQueryVO {

    private Train train;

    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private Integer duration;
    private String departure;
    private String arrival;
    private String departureCode;
    private String arrivalCode;
    private boolean departureFlag;
    private boolean arrivalFlag;

    private List<SeatClassFrontVO> seatClassFrontVOList;

    private List<TrainTypeVO>  trainTypeVOList;
}
