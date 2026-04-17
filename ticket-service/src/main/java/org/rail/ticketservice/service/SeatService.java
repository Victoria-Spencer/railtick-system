package org.rail.ticketservice.service;

import org.rail.ticketservice.pojo.vo.SeatBusinessVO;

import java.util.List;

public interface SeatService {
    void initAllTrainSeatCache();

    void initAllSeatOccupancyBitmap();

    List<Long> getFreeSeatIdsByBitmap(Long trainId,String departureCode,String arrivalCode);

    SeatBusinessVO getSeatBaseInfo(Long trainId, Long seatId);
}
