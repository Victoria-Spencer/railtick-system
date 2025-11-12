package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TrainSeatMapper {

    /**
     * 根据trainSeatClassId，统计可用座位数
     * @param trainSeatClassId
     * @param startSequence
     * @param endSequence
     * @return
     */
    Integer countAvailSeatsByClassId(Long trainSeatClassId, Integer startSequence, Integer endSequence);
}
