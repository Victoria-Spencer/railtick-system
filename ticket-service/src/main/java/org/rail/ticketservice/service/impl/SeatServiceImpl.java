package org.rail.ticketservice.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.rail.ticketservice.mapper.SeatIntervalOccupyMapper;
import org.rail.ticketservice.mapper.SeatMapper;
import org.rail.ticketservice.service.SeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SeatServiceImpl implements SeatService {

    @Autowired
    private SeatIntervalOccupyMapper seatIntervalOccupyMapper;
    @Autowired
    private SeatMapper seatMapper;

    @Override
    public void initAllTrainSeatCache() {

    }

    /**
     * 从数据库加载所有座位占用区间，初始化Redis Bitmap
     */
    @Override
    public void initAllSeatOccupancyBitmap() {
        log.info("开始从数据库加载座位占用区间 → 同步Redis Bitmap");

        // 1. 查询数据库中 所有有效 的座位占用记录
        List<SeatOccupancy> occupancyList = seatOccupancyMapper.selectValidAll();

        // 2. 遍历所有占用，批量设置到Bitmap
        for (SeatOccupancy occupancy : occupancyList) {
            seatRedisOperator.initSeatOccupancyFromDb(
                    occupancy.getTrainId(),
                    occupancy.getSeatId(),
                    occupancy.getStartStationSeq(),
                    occupancy.getEndStationSeq()
            );
        }
        log.info("座位占用区间Bitmap初始化完成，共加载{}条占用记录", occupancyList.size());
    }
}