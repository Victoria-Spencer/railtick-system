package org.rail.ticketservice.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rail.ticketservice.model.entity.SeatIntervalOccupy;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 座位占用记录 异步落库消息体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatOccupySyncMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 批量座位占用记录
     */
    private List<SeatIntervalOccupy> occupyList;
}