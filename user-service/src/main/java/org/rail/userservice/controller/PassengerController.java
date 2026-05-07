package org.rail.userservice.controller;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.rail.common.core.annotation.OperationLog;
import org.rail.common.core.result.PageResult;
import org.rail.common.core.result.Result;
import org.rail.userservice.pojo.dto.PsgrPageQueryDTO;
import org.rail.userservice.pojo.dto.PsgrUpdateDTO;
import org.rail.userservice.pojo.entity.Passenger;
import org.rail.userservice.service.PassengerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user-service/passenger")
@Validated
public class PassengerController {

    @Autowired
    private PassengerService passengerService;

    @PostMapping("/pageQuery")
    public Result<PageResult<Passenger>> pageQuery(@RequestBody @Validated PsgrPageQueryDTO psgrPageQueryDTO) {
        PageResult<Passenger> pageResult = passengerService.pageQuery(psgrPageQueryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/user/{id}")
    public Result<List<Passenger>> list(@PathVariable @NotNull(message = "用户ID不能为空") Long id) {
        List<Passenger> passengers = passengerService.getByUserId(id);
        return Result.success(passengers);
    }

    @GetMapping("/{id}")
    public Result<Passenger> passengerInfo(@PathVariable @NotNull(message = "用户ID不能为空") Long id) {
        Passenger passenger = passengerService.getById(id);
        return Result.success(passenger);
    }

    @OperationLog(value = "添加乘客信息", saveParam = true)
    @PostMapping("/save")
    public Result<Void> save(@RequestBody @Validated Passenger passenger) {
        passengerService.save(passenger);
        return Result.success();
    }

    @OperationLog(value = "修改乘客信息", saveParam = true)
    @PutMapping("/update")
    public Result<Void> update(@RequestBody @Validated PsgrUpdateDTO psgrUpdateDTO) {
        passengerService.update(psgrUpdateDTO);
        return Result.success();
    }

    @OperationLog(value = "删除乘客信息", saveParam = true)
    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam @NotEmpty(message = "乘客ID列表不能为空") List<Long> ids) {
        passengerService.deleteByIds(ids);
        return Result.success();
    }
}
