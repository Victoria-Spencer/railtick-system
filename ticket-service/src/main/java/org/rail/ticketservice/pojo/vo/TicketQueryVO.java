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

    /**
     * 列车属性
     */
    private Train train;

    // 出发时间
    private LocalDateTime departureTime;
    // 到达时间
    private LocalDateTime arrivalTime;
    // 历时
    private Integer duration;
    // 出发站点
    private String departure;
    // 到达站点
    private String arrival;
    // 始发站标识
    private boolean departureFlag;
    // 终点站标识
    private boolean arrivalFlag;

    /**
     * 席别信息
     */
    private List<SeatClassVO> seatClassList;
}
