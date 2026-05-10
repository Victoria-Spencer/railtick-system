package org.rail.userservice.controller;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.rail.common.core.annotation.OperationLog;
import org.rail.common.core.model.result.PageResult;
import org.rail.userservice.model.dto.PsgrPageQueryDTO;
import org.rail.userservice.model.dto.PsgrUpdateDTO;
import org.rail.userservice.model.entity.Passenger;
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
    public PageResult<Passenger> pageQuery(@RequestBody @Validated PsgrPageQueryDTO psgrPageQueryDTO) {
        return passengerService.pageQuery(psgrPageQueryDTO);
    }

    @GetMapping("/user/{id}")
    public List<Passenger> list(@PathVariable @NotNull(message = "用户ID不能为空") Long id) {
        return passengerService.getByUserId(id);
    }

    @GetMapping("/{id}")
    public Passenger passengerInfo(@PathVariable @NotNull(message = "用户ID不能为空") Long id) {
        return passengerService.getById(id);
    }

    @OperationLog(value = "添加乘客信息", saveParam = true)
    @PostMapping("/save")
    public void save(@RequestBody @Validated Passenger passenger) {
        passengerService.save(passenger);
    }

    @OperationLog(value = "修改乘客信息", saveParam = true)
    @PutMapping("/update")
    public void update(@RequestBody @Validated PsgrUpdateDTO psgrUpdateDTO) {
        passengerService.update(psgrUpdateDTO);
    }

    @OperationLog(value = "删除乘客信息", saveParam = true)
    @DeleteMapping("/delete")
    public void delete(@RequestParam @NotEmpty(message = "乘客ID列表不能为空") List<Long> ids) {
        passengerService.deleteByIds(ids);
    }
}
