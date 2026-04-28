package org.rail.ticketservice.controller;

import jakarta.validation.constraints.NotNull;
import org.rail.common.core.result.PageResult;
import org.rail.common.core.result.Result;
import org.rail.ticketservice.pojo.dto.StationPageQueryDTO;
import org.rail.ticketservice.pojo.vo.StationPageQueryVO;
import org.rail.ticketservice.pojo.vo.TrainStopStationVO;
import org.rail.ticketservice.service.StationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/ticket-service")
@Validated
public class StationController {

    @Autowired
    private StationService stationService;

    @PostMapping("/stations/page")
    public Result<PageResult<StationPageQueryVO>> pageQuery(@RequestBody StationPageQueryDTO stationPageQueryDTO) {
        PageResult<StationPageQueryVO> page = stationService.pageQueryStations(stationPageQueryDTO);
        return Result.success(page);
    }

    @GetMapping("train/{trainId}/stops")
    public Result<List<TrainStopStationVO>> getStops(@PathVariable("trainId") @NotNull(message = "车次ID不能为空") Long trainId) {
        List<TrainStopStationVO> infoList = stationService.getStopsByTrainId(trainId);
        return Result.success(infoList);
    }
}
