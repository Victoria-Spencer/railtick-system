package org.rail.ticketservice.controller;

import org.rail.commonservice.result.PageResult;
import org.rail.commonservice.result.Result;
import org.rail.ticketservice.pojo.dto.StationPageQueryDTO;
import org.rail.ticketservice.pojo.vo.StationPageQueryVO;
import org.rail.ticketservice.pojo.vo.TrainStopStationVO;
import org.rail.ticketservice.service.StationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/ticket-service")
public class StationController {

    @Autowired
    private StationService stationService;

    @GetMapping("/stations/page")
    public Result<PageResult<StationPageQueryVO>> pageQuery(@RequestBody StationPageQueryDTO stationPageQueryDTO) {
        PageResult<StationPageQueryVO> page = stationService.pageQueryStations(stationPageQueryDTO);
        return Result.success(page);
    }

    @GetMapping("train/{trainId}/stops")
    public Result<List<TrainStopStationVO>> getStops(@PathVariable("trainId") Long trainId) {
        List<TrainStopStationVO> infoList = stationService.getStopsByTrainId(trainId);
        return Result.success(infoList);
    }
}
