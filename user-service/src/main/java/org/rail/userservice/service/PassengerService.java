package org.rail.userservice.service;

import org.rail.common.core.model.result.PageResult;
import org.rail.userservice.model.dto.PsgrPageQueryDTO;
import org.rail.userservice.model.dto.PsgrUpdateDTO;
import org.rail.userservice.model.entity.Passenger;

import java.util.List;

public interface PassengerService {

    /**
     * 分页查询
     * @param psgrPageQueryDTO 分页查询参数
     * @return 分页结果
     */
    PageResult<Passenger> pageQuery(PsgrPageQueryDTO psgrPageQueryDTO);

    /**
     * 根据用户id查询所有乘车人信息
     * @param userId 用户id
     * @return 乘车人列表
     */
    List<Passenger> getByUserId(Long userId);

    /**
     * 根据乘车人id查询乘车人信息
     * @param id 乘车人id
     * @return 乘车人信息
     */
    Passenger getById(Long id);

    /**
     * 添加新的乘车人
     * @param passenger 乘车人信息
     */
    void save(Passenger passenger);

    /**
     * 更新乘车人信息
     * @param psgrUpdateDTO 乘车人更新信息
     */
    void update(PsgrUpdateDTO psgrUpdateDTO);

    /**
     * 根据ids移除乘车人
     * @param ids 乘车人id列表
     */
    void deleteByIds(List<Long> ids);
}
