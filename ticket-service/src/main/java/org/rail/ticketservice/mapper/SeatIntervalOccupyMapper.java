package org.rail.ticketservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.rail.api.dto.UpdateSeatStatusDTO;
import org.rail.ticketservice.pojo.dto.IntervalOccupyDTO;
import org.rail.ticketservice.pojo.dto.SeatIntervalOccupyModifyDTO;
import org.rail.ticketservice.pojo.entity.SeatIntervalOccupy;

import java.util.List;

@Mapper
public interface SeatIntervalOccupyMapper {

    /**
     * 查询列车下指定的席别类型的指定座位的占用区间
     * @param updateSeatStatusDTOList 包含列车id、席别类型id、座位id的列表
     * @return 包含占用区间信息的列表
     */
    List<IntervalOccupyDTO> getIntervalOccupy(List<UpdateSeatStatusDTO> updateSeatStatusDTOList);

    /**
     * 批量插入座位区间占用记录
     * @param seatIntervalOccupyList 包含要插入的座位区间占用记录的列表
     */
    void batchInsertSIOOccupyRecords(List<SeatIntervalOccupy> seatIntervalOccupyList);

    /**
     * 查询所有有效的座位区间占用记录
     * @return 包含有效座位区间占用记录的列表
     */
    List<SeatIntervalOccupy> selectValidAll();

    /**
     * 批量更新座位区间占用记录
     * @param occupyModifyDTOS 包含要更新的座位区间占用记录信息的列表
     */
//    void batchUpdateSIOOccupyRecodes(List<SeatIntervalOccupyModifyDTO> occupyModifyDTOS);
}
