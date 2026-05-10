package org.rail.ticketservice.controller;

import jakarta.validation.constraints.NotNull;
import org.rail.common.core.model.result.PageResult;
import org.rail.ticketservice.model.dto.StationPageQueryDTO;
import org.rail.ticketservice.model.vo.StationPageQueryVO;
import org.rail.ticketservice.model.vo.TrainStopStationVO;
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
    public PageResult<StationPageQueryVO> pageQuery(@RequestBody StationPageQueryDTO stationPageQueryDTO) {
        return stationService.pageQueryStations(stationPageQueryDTO);
    }

    @GetMapping("train/{trainId}/stops")
    public List<TrainStopStationVO> getStops(@PathVariable("trainId") @NotNull(message = "车次ID不能为空") Long trainId) {
        return stationService.getStopsByTrainId(trainId);
    }
}
