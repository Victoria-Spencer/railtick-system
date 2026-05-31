package org.rail.userservice.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.TypeReference;
import com.github.pagehelper.PageHelper;
import org.rail.api.dto.PassengerRemoteDTO;
import org.rail.userservice.constant.UserRedisConstants;
import org.rail.common.core.context.RequestContext;
import org.rail.common.core.context.RequestContextHolder;
import org.rail.common.core.exception.BizException;
import org.rail.common.redis.api.ICacheClient;
import org.rail.common.core.constant.RedisCommonConstants;
import org.rail.common.core.model.result.PageResult;
import org.rail.userservice.constant.VerifyStatusConstants;
import org.rail.userservice.mapper.PassengerMapper;
import org.rail.userservice.model.dto.PsgrDTO;
import org.rail.userservice.model.dto.PsgrPageQueryDTO;
import org.rail.userservice.model.dto.PsgrUpdateDTO;
import org.rail.userservice.model.entity.Passenger;
import org.rail.userservice.model.vo.PsgrVO;
import org.rail.userservice.service.PassengerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PassengerServiceImpl implements PassengerService {

    @Autowired
    private PassengerMapper passengerMapper;
    @Autowired
    private ICacheClient cacheClient;

    /**
     * 分页查询（管理员）
     * @param psgrPageQueryDTO 分页查询参数
     * @return 分页结果
     */
    @Override
    public PageResult<PsgrVO> pageQuery(PsgrPageQueryDTO psgrPageQueryDTO) {
        psgrPageQueryDTO.setVerifyStatus(VerifyStatusConstants.UNREVIEWED);
        PageHelper.startPage(psgrPageQueryDTO.getPageNumber(), psgrPageQueryDTO.getPageSize());
        List<Passenger> passengerList = passengerMapper.query(psgrPageQueryDTO);

        List<PsgrVO> voList = passengerList.stream()
                .map(passenger -> BeanUtil.copyProperties(passenger, PsgrVO.class))
                .collect(Collectors.toList());

        return new PageResult<>(voList);
    }

    /**
     * 查询该用户所有乘车人信息
     * @return 乘车人列表
     */
    @Override
    public List<PsgrVO> list() {
        RequestContext context = RequestContextHolder.getRequestContext();
        if (context == null|| context.getAccountId() == null) {
            return new ArrayList<>();
        }
        Long userId = Long.valueOf(context.getAccountId());

        TypeReference<List<Passenger>> typeRef = new TypeReference<>() {};
        List<Passenger> passengerList = cacheClient.queryWithMutex(
                UserRedisConstants.RAIL_PASSENGER_LIST_USER_PREFIX,
                userId,
                typeRef,
                id -> passengerMapper.getByUserId(id),
                RedisCommonConstants.RAIL_DEFAULT_TTL,
                TimeUnit.MINUTES
        );

        return passengerList.stream()
                .map(passenger -> BeanUtil.copyProperties(passenger, PsgrVO.class))
                .collect(Collectors.toList());
    }

    /**
     * 根据乘车人id查询乘车人信息
     * @param id 乘车人id
     * @return 乘车人信息
     */
    @Override
    public PsgrVO getById(Long id) {
        Passenger passenger = passengerMapper.getById(id);

        if (passenger == null) {
            throw new BizException("乘车人不存在");
        }

        // 权限校验
        RequestContext context = RequestContextHolder.getRequestContext();
        if (context == null|| context.getAccountId() == null) {
            throw new BizException("未获取到用户信息");
        }
        Long currentUserId = Long.valueOf(context.getAccountId());
        if (!passenger.getUserId().equals(currentUserId)) {
            throw new BizException("无权限访问该乘车人信息");
        }

        return BeanUtil.copyProperties(passenger, PsgrVO.class);
    }

    /**
     * 添加新的乘车人
     * @param dto 乘车人信息
     */
    @Override
    public void save(PsgrDTO dto) {
        Passenger passenger = BeanUtil.copyProperties(dto, Passenger.class);

        RequestContext context = RequestContextHolder.getRequestContext();
        if (context == null|| context.getAccountId() == null) {
            throw new BizException("未获取到用户信息");
        }
        Long userId = Long.valueOf(context.getAccountId());
        passenger.setUserId(userId);

        // 设置审核状态
        passenger.setVerifyStatus(VerifyStatusConstants.UNREVIEWED);

        passenger.setCreateTime(LocalDateTime.now());
        passenger.setUpdateTime(LocalDateTime.now());

        passengerMapper.insert(passenger);
    }

    /**
     * 更新乘车人信息
     * @param psgrUpdateDTO 乘车人更新信息
     */
    /*@AutoClearAggCache(
            keySource = AutoClearAggCache.KeySource.THREAD_LOCAL,
            singleKeyPrefix = RedisConstants.RAIL_PASSENGER_LIST_USER_PREFIX
    )*/
    @Override
    public void update(PsgrUpdateDTO psgrUpdateDTO) {
        Passenger passenger = BeanUtil.copyProperties(psgrUpdateDTO, Passenger.class);
        passenger.setUpdateTime(LocalDateTime.now());
        passengerMapper.updateById(passenger);

        RequestContext context = RequestContextHolder.getRequestContext();
        if (context == null || context.getAccountId() == null) {
            throw new BizException("未获取到用户信息");
        }
        String userId = context.getAccountId();
        cacheClient.autoClearAggCache(UserRedisConstants.RAIL_PASSENGER_LIST_USER_PREFIX + userId);
    }

    /**
     * 根据ids移除乘车人
     * @param ids 乘车人id列表
     */
    /*@AutoClearAggCache(
            keySource = AutoClearAggCache.KeySource.THREAD_LOCAL,
            singleKeyPrefix = RedisConstants.RAIL_PASSENGER_LIST_USER_PREFIX
    )*/
    @Override
    public void deleteByIds(List<Long> ids) {
        RequestContext context = RequestContextHolder.getRequestContext();
        if (context == null || context.getAccountId() == null) {
            throw new BizException("未获取到用户信息");
        }
        Long userId = Long.valueOf(context.getAccountId());
        passengerMapper.batchDelete(ids, userId);

        cacheClient.autoClearAggCache(UserRedisConstants.RAIL_PASSENGER_LIST_USER_PREFIX + userId);
    }

    /**
     * 批量查询乘车人信息
     * @param passengerIds 乘客ID列表
     * @return 乘车人信息列表
     */
    @Override
    public List<PassengerRemoteDTO> batchListPassenger(List<Long> passengerIds) {
        List<Passenger> passengerList = passengerMapper.selectBatchIds(passengerIds);

        Map<Long, Passenger> passengerMap = passengerList.stream()
                .collect(Collectors.toMap(Passenger::getId, passenger -> passenger));

        // 保证顺序一致
        return passengerIds.stream()
                .map(passengerId -> {
                    Passenger passenger = passengerMap.get(passengerId);
                    if (passenger == null) {
                        return null;
                    }
                    return BeanUtil.copyProperties(passenger, PassengerRemoteDTO.class);
                })
                .collect(Collectors.toList());
    }
}
