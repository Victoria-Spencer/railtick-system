package org.rail.userservice.service;

import org.rail.api.dto.PassengerRemoteDTO;
import org.rail.common.core.model.result.PageResult;
import org.rail.userservice.model.dto.PsgrDTO;
import org.rail.userservice.model.dto.PsgrPageQueryDTO;
import org.rail.userservice.model.dto.PsgrUpdateDTO;
import org.rail.userservice.model.vo.PsgrVO;

import java.util.List;

public interface PassengerService {

    /**
     * 分页查询
     * @param psgrPageQueryDTO 分页查询参数
     * @return 分页结果
     */
    PageResult<PsgrVO> pageQuery(PsgrPageQueryDTO psgrPageQueryDTO);

    /**
     * 查询该用户所有乘车人信息
     * @return 乘车人列表
     */
    List<PsgrVO> list();

    /**
     * 根据乘车人id查询乘车人信息
     * @param id 乘车人id
     * @return 乘车人信息
     */
    PsgrVO getById(Long id);

    /**
     * 添加新的乘车人
     * @param dto 乘车人信息
     */
    void save(PsgrDTO dto);

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

    /**
     * 批量根据乘客ID查询真实信息
     * @param passengerIds 乘客ID列表
     * @return 乘客信息列表
     */
    List<PassengerRemoteDTO> batchListPassenger(List<Long> passengerIds);
}
