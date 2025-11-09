package org.rail.userservice.service;

import org.rail.commonservice.result.PageResult;
import org.rail.userservice.pojo.dto.PsgrPageQueryDTO;
import org.rail.userservice.pojo.dto.PsgrUpdateDTO;
import org.rail.userservice.pojo.entity.Passenger;

import java.util.List;

public interface PassengerService {

    /**
     * 分页查询
     * @param psgrPageQueryDTO
     * @return
     */
    PageResult<Passenger> pageQuery(PsgrPageQueryDTO psgrPageQueryDTO);

    /**
     * 根据乘车人id查询乘车人信息
     * @param id
     * @return
     */
    Passenger getById(Long id);

    /**
     * 添加新的乘车人
     * @param passenger
     */
    void save(Passenger passenger);

    /**
     * 更新乘车人信息
     * @param psgrUpdateDTO
     */
    void update(PsgrUpdateDTO psgrUpdateDTO);

    /**
     * 根据ids移除乘车人
     * @param ids
     */
    void deleteByIds(List<Long> ids);
}
