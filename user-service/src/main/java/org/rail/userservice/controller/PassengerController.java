package org.rail.userservice.controller;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.rail.api.dto.PassengerRemoteDTO;
import org.rail.common.core.model.result.PageResult;
import org.rail.userservice.model.dto.PsgrDTO;
import org.rail.userservice.model.dto.PsgrPageQueryDTO;
import org.rail.userservice.model.dto.PsgrUpdateDTO;
import org.rail.userservice.model.vo.PsgrVO;
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
    public PageResult<PsgrVO> pageQuery(@RequestBody PsgrPageQueryDTO psgrPageQueryDTO) {
        return passengerService.pageQuery(psgrPageQueryDTO);
    }

    @GetMapping("/user/list")
    public List<PsgrVO> list() {
        return passengerService.list();
    }

    @GetMapping("/{id}")
    public PsgrVO passengerInfo(@PathVariable @NotNull(message = "乘车人ID不能为空") Long id) {
        return passengerService.getById(id);
    }

    @PostMapping("/save")
    public void save(@RequestBody @Validated PsgrDTO passengerDTO) {
        passengerService.save(passengerDTO);
    }

    @PutMapping("/update")
    public void update(@RequestBody @Validated PsgrUpdateDTO psgrUpdateDTO) {
        passengerService.update(psgrUpdateDTO);
    }

    @DeleteMapping("/delete")
    public void delete(@RequestParam @NotEmpty(message = "乘客ID列表不能为空") List<Long> ids) {
        passengerService.deleteByIds(ids);
    }

    @PostMapping("/batch")
    List<PassengerRemoteDTO> batchListPassenger(@RequestBody @NotEmpty(message = "乘客ID列表不能为空") List<Long> passengerIds) {
        return passengerService.batchListPassenger(passengerIds);
    }
}
