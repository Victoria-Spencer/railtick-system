package org.rail.ticketservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * 列车经停站缓存DTO
 * 仅保留业务核心使用字段，无任何方法逻辑
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainStopStationCacheDTO {

    // 站点ID -> 站点顺序 映射
    private Map<Long, Integer> stationId2SeqMap;

    // 终点站序号
    private Integer terminalSeq;
}