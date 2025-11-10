package org.rail.userservice.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageHelper;
import lombok.val;
import org.rail.commonservice.result.PageResult;
import org.rail.commonservice.utils.ThreadLocalUtils;
import org.rail.userservice.constant.VerifyStatus;
import org.rail.userservice.mapper.PassengerMapper;
import org.rail.userservice.pojo.dto.PsgrPageQueryDTO;
import org.rail.userservice.pojo.dto.PsgrUpdateDTO;
import org.rail.userservice.pojo.entity.Passenger;
import org.rail.userservice.service.PassengerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PassengerServiceImpl implements PassengerService {

    @Autowired
    private PassengerMapper passengerMapper;

    /**
     * 分页查询
     * @param psgrPageQueryDTO
     * @return
     */
    public PageResult<Passenger> pageQuery(PsgrPageQueryDTO psgrPageQueryDTO) {
        // 1. 开启分页（pageNum：页码，pageSize：每页条数）
        PageHelper.startPage(psgrPageQueryDTO.getPageNumber(), psgrPageQueryDTO.getPageSize());

        // 2. 执行查询（PageHelper会自动拦截该查询，拼接LIMIT分页）
        List<Passenger> userList = passengerMapper.query(psgrPageQueryDTO);

        psgrPageQueryDTO.setVerifyStatus(VerifyStatus.UNREVIEWED);

        // 封装成PageResult返回
        return new PageResult<>(userList);
    }

    /**
     * 根据乘车人id查询乘车人信息
     * @param id
     * @return
     */
    public Passenger getById(Long id) {
        return passengerMapper.getById(id);
    }

    /**
     * 添加新的乘车人
     * @param passenger
     */
    public void save(Passenger passenger) {
        // 从线程中获取乘车人对应的用户标识
        Long userId = Long.valueOf(ThreadLocalUtils.get());
        passenger.setUserId(userId);

        // 设置审核状态
        passenger.setVerifyStatus(VerifyStatus.UNREVIEWED);

        // 设置变动时间
        passenger.setCreateTime(LocalDateTime.now());
        passenger.setUpdateTime(LocalDateTime.now());

        passengerMapper.insert(passenger);
    }

    /**
     * 更新乘车人信息
     * @param psgrUpdateDTO
     */
    public void update(PsgrUpdateDTO psgrUpdateDTO) {
        Passenger passenger = BeanUtil.copyProperties(psgrUpdateDTO, Passenger.class);
        passenger.setUpdateTime(LocalDateTime.now());
        passengerMapper.updateById(passenger);
    }

    /**
     * 根据ids移除乘车人
     * @param ids
     */
    public void deleteByIds(List<Long> ids) {
        passengerMapper.batchDelete(ids);
    }
}
