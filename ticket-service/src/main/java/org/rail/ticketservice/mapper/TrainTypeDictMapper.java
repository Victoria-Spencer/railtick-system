package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.rail.ticketservice.model.vo.TrainTypeVO;

import java.util.List;

@Mapper
public interface TrainTypeDictMapper {

    /**
     * 根据列车ID查询列车类型信息
     * @param trainId 列车ID
     * @return 列车类型信息列表
     */
    List<TrainTypeVO> getTrainTypeDictByTrainId(Long trainId);
}
