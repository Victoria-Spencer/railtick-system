package org.rail.ticketservice.controller;

import org.rail.commonservice.result.PageResult;
import org.rail.commonservice.result.Result;
import org.rail.ticketservice.pojo.dto.StationPageQueryDTO;
import org.rail.ticketservice.pojo.entity.TrainStopStationInfo;
import org.rail.ticketservice.pojo.vo.StationPageQueryVO;
import org.rail.ticketservice.service.StationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/ticket-service")
public class StationController {

    @Autowired
    private StationService stationService;

    @GetMapping("/stations/pageQuery")
    public Result<PageResult<StationPageQueryVO>> pageQuery(StationPageQueryDTO stationPageQueryDTO) {
        PageResult<StationPageQueryVO> page = stationService.pageQueryStations(stationPageQueryDTO);
        return Result.success(page);
    }

    @GetMapping("train/{trainId}/stops")
    public Result<List<TrainStopStationInfo>> getStops(@PathVariable("trainId") Integer trainId) {
        List<TrainStopStationInfo> infoList = stationService.getStopsByTrainId(trainId);
        return Result.success(infoList);
    }
}
