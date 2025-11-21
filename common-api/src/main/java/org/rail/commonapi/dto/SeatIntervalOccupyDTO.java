package org.rail.commonapi.dto;

import lombok.Data;

import java.util.List;

/**
 * 更新座位占用区间
 */
@Data
public class SeatIntervalOccupyDTO {

    private List<SeatIntervalOccupyInsertDTO> insertDTOList;
    private List<SeatIntervalOccupyUpdateDTO> updateDTOList;
}
