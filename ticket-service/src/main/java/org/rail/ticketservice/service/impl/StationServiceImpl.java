package org.rail.ticketservice.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.rail.common.core.model.result.PageResult;
import org.rail.ticketservice.mapper.StationMapper;
import org.rail.ticketservice.model.dto.StationPageQueryDTO;
import org.rail.ticketservice.model.entity.Station;
import org.rail.ticketservice.model.vo.StationPageQueryVO;
import org.rail.ticketservice.model.vo.TrainStopStationVO;
import org.rail.ticketservice.service.StationService;
import org.rail.ticketservice.task.StationLocalCacheTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
public class StationServiceImpl implements StationService {

    @Autowired
    private StationMapper stationMapper;
    @Autowired
    private StationLocalCacheTask stationCacheTask;

    /**
     * 根据查询类型或名称分页查询站点列表
     * @param dto 站点分页查询参数（包含queryType、keyword、pageNumber、pageSize）
     * @return 分页结果（包含总记录数和当前页数据列表）
     */
    @Override
    public PageResult<StationPageQueryVO> pageQueryStations(StationPageQueryDTO dto) {
        // 1. 从本地缓存获取全量站点数据
        List<Station> allStations = stationCacheTask.getAllStations();
        if (allStations.isEmpty()) {
            log.warn("站点本地缓存为空，返回空结果");
            return null;
        }

        // 2. 内存中模糊过滤（名称/拼音包含关键词）
        List<Station> filteredStations = getFilteredStations(dto, allStations);
        long total = filteredStations.size();

        // 3. 内存中分页
        List<Station> pageData = pageStationData(filteredStations, dto.getPageNumber(), dto.getPageSize());

        // 4. 转换为VO返回
        List<StationPageQueryVO> voList = pageData.stream()
                .map(station -> new StationPageQueryVO(station.getName(), station.getCode(), station.getSpell()))
                .collect(Collectors.toList());

        return new PageResult<>(total, voList, dto.getPageSize());
      }

    /**
     * 根据查询类型或关键词过滤站点列表
     * @param dto 站点分页查询参数
     * @param allStations 全量站点列表
     * @return 过滤后的站点列表
     */
    private List<Station> getFilteredStations(StationPageQueryDTO dto, List<Station> allStations) {
        Integer queryType = dto.getQueryType();
        String keyword = dto.getKeyword();
        List<Station> filteredStations;
        if (queryType != null) {
            filteredStations = allStations.stream()
                    .filter(station -> filterStationByQueryType(station, queryType))
                    .collect(Collectors.toList());
        } else {
            filteredStations = allStations.stream()
                    .filter(station -> filterStationByKeyword(station, keyword))
                    .collect(Collectors.toList());
        }
        return filteredStations;
    }

    /**
     * 按queryType分组过滤
     */
    private boolean filterStationByQueryType(Station station, Integer queryType) {
        return station.getQueryType() != null && station.getQueryType().equals(queryType);
    }

    /**
     * 按keyword模糊匹配（名称/拼音包含关键词，忽略大小写）
     */
    private boolean filterStationByKeyword(Station station, String keyword) {
        // 无关键词：返回所有
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        // 关键词匹配名称或拼音（忽略大小写）
        String lowerKeyword = keyword.toLowerCase();
        return station.getName().toLowerCase().contains(lowerKeyword)
                || station.getSpell().toLowerCase().contains(lowerKeyword);
    }

    /**
     * 内存分页逻辑（避免下标越界）
     */
    private List<Station> pageStationData(List<Station> data, int pageNum, int pageSize) {
        int start = (pageNum - 1) * pageSize;
        // 起始下标超过数据长度：返回空列表
        if (start >= data.size()) {
            return List.of();
        }
        int end = Math.min(start + pageSize, data.size());
        return data.subList(start, end);
    }

    /**
     * 根据列车id查询列车经停站信息
     * @return 列车经停站信息列表
     */
    @Override
    public List<TrainStopStationVO> getStopsByTrainId(Long trainId) {
        // 获取列车经停站列表
        List<TrainStopStationVO> TrainStopStationVOS = stationMapper.batchQueryByTrainId(trainId);

        for (TrainStopStationVO trainStopStationVO : TrainStopStationVOS) {
            LocalDateTime arrivalTime = trainStopStationVO.getArrivalTime();
            LocalDateTime departureTime = trainStopStationVO.getDepartureTime();

            // 计算两个时间的差值（分钟）
            long stopoverMinutes = 0L;
            if(arrivalTime!=null && departureTime!=null) {
                stopoverMinutes = Duration.between(arrivalTime, departureTime).toMinutes();
            }
            trainStopStationVO.setStopoverTime(stopoverMinutes);
        }

        return TrainStopStationVOS;
    }
}
