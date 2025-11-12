package org.rail.userservice.controller;

import org.rail.commonservice.result.PageResult;
import org.rail.commonservice.result.Result;
import org.rail.userservice.pojo.dto.PsgrPageQueryDTO;
import org.rail.userservice.pojo.dto.PsgrUpdateDTO;
import org.rail.userservice.pojo.entity.Passenger;
import org.rail.userservice.service.PassengerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user-service/passenger")
public class PassengerController {

    @Autowired
    private PassengerService passengerService;

    @GetMapping("/pageQuery")
    public Result<PageResult<Passenger>> pageQuery(@RequestBody PsgrPageQueryDTO psgrPageQueryDTO) {
        PageResult<Passenger> pageResult = passengerService.pageQuery(psgrPageQueryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/user/{id}")
    public Result<List<Passenger>> list(@PathVariable Long id) {
        List<Passenger> passengers = passengerService.getByUserId(id);
        return Result.success(passengers);
    }

    @GetMapping("/{id}")
    public Result<Passenger> passengerInfo(@PathVariable Long id) {
        Passenger passenger = passengerService.getById(id);
        return Result.success(passenger);
    }

    @PostMapping("/save")
    public Result save(@RequestBody Passenger passenger) {
        passengerService.save(passenger);
        return Result.success();
    }

    @PutMapping("/update")
    public Result update(@RequestBody PsgrUpdateDTO psgrUpdateDTO) {
        passengerService.update(psgrUpdateDTO);
        return Result.success();
    }

    @DeleteMapping("/delete")
    public Result delete(@RequestParam List<Long> ids) {
        passengerService.deleteByIds(ids);
        return Result.success();
    }
}
