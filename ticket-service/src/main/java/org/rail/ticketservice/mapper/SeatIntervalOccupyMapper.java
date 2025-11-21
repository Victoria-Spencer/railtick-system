package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.rail.commonapi.dto.SeatIntervalOccupyUpdateDTO;
import org.rail.commonapi.dto.UpdateSeatStatusDTO;
import org.rail.ticketservice.pojo.dto.IntervalOccupyDTO;
import org.rail.ticketservice.pojo.dto.SeatIntervalOccupyModifyDTO;
import org.rail.ticketservice.pojo.entity.SeatIntervalOccupy;

import java.util.List;

@Mapper
public interface SeatIntervalOccupyMapper {

    /**
     * 查询列车下指定的席别类型的指定座位的占用区间
     * @param updateSeatStatusDTOList
     * @return
     */
    List<IntervalOccupyDTO> getIntervalOccupy(List<UpdateSeatStatusDTO> updateSeatStatusDTOList);

    /**
     * 批量插入座位区间占用记录
     * @param seatIntervalOccupyList
     */
    void batchInsertSIOOccupyRecords(List<SeatIntervalOccupy> seatIntervalOccupyList);

    /**
     * 批量更新座位区间占用记录
     * @param occupyModifyDTOS
     */
    void batchUpdateSIOOccupyRecodes(List<SeatIntervalOccupyModifyDTO> occupyModifyDTOS);
}
