package org.rail.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

/**
 * 更新座位占用区间
 */
@Data
public class SeatIntervalOccupyDTO {

    private List<SeatIntervalOccupyInsertDTO> insertDTOList;
    private List<SeatIntervalOccupyUpdateDTO> updateDTOList;

    /**
     * 判断当前DTO是否不包含任何有效数据
     */
    // 添加@JsonIgnore，避免Jackson将其序列化为"empty"字段
    @JsonIgnore
    public boolean isEmpty() {
        // 两个列表都为null或空时，视为“空DTO”
        boolean insertEmpty = insertDTOList == null || insertDTOList.isEmpty();
        boolean updateEmpty = updateDTOList == null || updateDTOList.isEmpty();
        return insertEmpty && updateEmpty;
    }
}
