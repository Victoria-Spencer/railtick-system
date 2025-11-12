package org.rail.ticketservice.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.json.JSONUtil;
import com.github.pagehelper.PageHelper;
import lombok.val;
import org.rail.commonservice.result.PageResult;
import org.rail.ticketservice.mapper.StationMapper;
import org.rail.ticketservice.pojo.dto.StationPageQueryDTO;
import org.rail.ticketservice.pojo.vo.StationPageQueryVO;
import org.rail.ticketservice.pojo.vo.TrainStopStationVO;
import org.rail.ticketservice.service.StationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
public class StationServiceImpl implements StationService {

    @Autowired
    private StationMapper stationMapper;

    /**
     * 根据查询类型或名称分页查询站点列表
     * @param stationPageQueryDTO
     * @return
     */
    public PageResult<StationPageQueryVO> pageQueryStations(StationPageQueryDTO stationPageQueryDTO) {
        // 开始分页
        PageHelper.startPage(stationPageQueryDTO.getPageNumber(), stationPageQueryDTO.getPageSize());
        // 查询站点列表
        List<StationPageQueryVO> list = stationMapper.pageQuery(stationPageQueryDTO);
        return new PageResult<>(list);
    }

    /**
     * 根据列车id查询列车经停站信息
     * @return
     */
    public List<TrainStopStationVO> getStopsByTrainId(Long trainId) {
        // 获取列车经停站列表
        List<TrainStopStationVO> TrainStopStationVOS = stationMapper.batchQueryByTrainId(trainId);

        for (TrainStopStationVO trainStopStationVO : TrainStopStationVOS) {
            LocalDateTime arrivalTime = trainStopStationVO.getArrivalTime();
            LocalDateTime departureTime = trainStopStationVO.getDepartureTime();

            // 计算两个时间的差值（分钟）
            Long stopoverMinutes = 0L;
            if(arrivalTime!=null && departureTime!=null) {
                stopoverMinutes = Duration.between(arrivalTime, departureTime).toMinutes();
            }
            // 赋值（假设 stopoverTime 是 Long 或 long 类型）
            trainStopStationVO.setStopoverTime(stopoverMinutes);
        }

        return TrainStopStationVOS;
    }
}
