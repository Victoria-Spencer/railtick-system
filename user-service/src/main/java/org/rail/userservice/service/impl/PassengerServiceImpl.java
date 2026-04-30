package org.rail.userservice.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.TypeReference;
import com.github.pagehelper.PageHelper;
import org.rail.common.core.context.RequestContext;
import org.rail.common.core.context.RequestContextHolder;
import org.rail.common.redis.api.ICacheClient;
import org.rail.common.redis.constant.RedisConstants;
import org.rail.common.core.result.PageResult;
import org.rail.userservice.constant.VerifyStatusConstants;
import org.rail.userservice.mapper.PassengerMapper;
import org.rail.userservice.pojo.dto.PsgrPageQueryDTO;
import org.rail.userservice.pojo.dto.PsgrUpdateDTO;
import org.rail.userservice.pojo.entity.Passenger;
import org.rail.userservice.service.PassengerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    public PageResult<Passenger> pageQuery(PsgrPageQueryDTO psgrPageQueryDTO) {
        // 1. 开启分页（pageNum：页码，pageSize：每页条数）
        PageHelper.startPage(psgrPageQueryDTO.getPageNumber(), psgrPageQueryDTO.getPageSize());

        // 2. 执行查询（PageHelper会自动拦截该查询，拼接LIMIT分页）
        List<Passenger> userList = passengerMapper.query(psgrPageQueryDTO);

        psgrPageQueryDTO.setVerifyStatus(VerifyStatusConstants.UNREVIEWED);

        // 封装成PageResult返回
        return new PageResult<>(userList);
    }

    /**
     * 根据用户id查询所有乘车人信息
     * @param userId 用户id
     * @return 乘车人列表
     */
    @Override
    public List<Passenger> getByUserId(Long userId) {
        TypeReference<List<Passenger>> typeRef = new TypeReference<>() {};
        return cacheClient.queryWithMutex(
                RedisConstants.RAIL_PASSENGER_LIST_USER_PREFIX,
                userId,
                typeRef,
                id -> passengerMapper.getByUserId(id),
                RedisConstants.RAIL_DEFAULT_TTL,
                TimeUnit.MINUTES
        );
    }

    /**
     * 根据乘车人id查询乘车人信息
     * @param id 乘车人id
     * @return 乘车人信息
     */
    @Override
    public Passenger getById(Long id) {
        return passengerMapper.getById(id);
    }

    /**
     * 添加新的乘车人
     * @param passenger 乘车人信息
     */
    @Override
    public void save(Passenger passenger) {
        // 从线程中获取乘车人对应的用户标识
        RequestContext requestContext = RequestContextHolder.getRequestContext();
        Long userId = Long.valueOf(requestContext.getAccountId());
        passenger.setUserId(userId);

        // 设置审核状态
        passenger.setVerifyStatus(VerifyStatusConstants.UNREVIEWED);

        // 设置变动时间
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

        RequestContext requestContext = RequestContextHolder.getRequestContext();
        String userId = requestContext.getAccountId();
        cacheClient.autoClearAggCache(RedisConstants.RAIL_PASSENGER_LIST_USER_PREFIX + userId);
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
        passengerMapper.batchDelete(ids);

        RequestContext requestContext = RequestContextHolder.getRequestContext();
        String userId = requestContext.getAccountId();
        cacheClient.autoClearAggCache(RedisConstants.RAIL_PASSENGER_LIST_USER_PREFIX + userId);
    }
}
