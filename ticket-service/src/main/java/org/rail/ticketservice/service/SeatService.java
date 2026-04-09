package org.rail.ticketservice.service;

public interface SeatService {
    void initAllTrainSeatCache();

    void initAllSeatOccupancyBitmap();
}
