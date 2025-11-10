package org.rail.ticketservice.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.json.JSONUtil;
import com.github.pagehelper.PageHelper;
import lombok.val;
import org.rail.commonservice.result.PageResult;
import org.rail.ticketservice.mapper.StationMapper;
import org.rail.ticketservice.pojo.dto.StationPageQueryDTO;
import org.rail.ticketservice.pojo.entity.TrainStopStationInfo;
import org.rail.ticketservice.pojo.vo.StationPageQueryVO;
import org.rail.ticketservice.service.StationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    public List<TrainStopStationInfo> getStopsByTrainId(Integer trainId) {
        // 获取列车经停站jsonString
        String info = stationMapper.getStopsByTrainId(trainId);

        // 转为list集合
        List<TrainStopStationInfo> list = JSONUtil.toList(info, TrainStopStationInfo.class);
        return list;
    }
}
