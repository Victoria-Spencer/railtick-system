package org.rail.userservice.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.TypeReference;
import com.fasterxml.jackson.databind.EnumNamingStrategies;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.rail.commonservice.annotation.AutoClearAggCache;
import org.rail.commonservice.constant.RedisConstants;
import org.rail.commonservice.result.PageResult;
import org.rail.commonservice.utils.CacheClient;
import org.rail.commonservice.utils.ThreadLocalUtils;
import org.rail.userservice.constant.VerifyStatusConstants;
import org.rail.userservice.mapper.PassengerMapper;
import org.rail.userservice.pojo.dto.PsgrPageQueryDTO;
import org.rail.userservice.pojo.dto.PsgrUpdateDTO;
import org.rail.userservice.pojo.entity.Passenger;
import org.rail.userservice.service.PassengerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PassengerServiceImpl implements PassengerService {

    @Autowired
    private PassengerMapper passengerMapper;
    @Autowired
    private CacheClient cacheClient;

    /**
     * 分页查询（管理员）
     * @param psgrPageQueryDTO
     * @return
     */
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
     * @param userId
     * @return
     */
    public List<Passenger> getByUserId(Long userId) {
        if (userId == null) {
            log.warn("查询乘客列表失败：userId 不能为空");
            return Collections.emptyList();
        }
        // 缓存乘客信息
        TypeReference<List<Passenger>> typeRef = new TypeReference<List<Passenger>>() {};
        return cacheClient.queryWithMutex(
                RedisConstants.RAIL_PASSENGER_LIST_USER_PREFIX,
                userId,
                typeRef, // 直接传泛型类型
                id -> passengerMapper.getByUserId(id), // 缓存未命中时，查库
                RedisConstants.RAIL_DEFAULT_TTL,
                TimeUnit.MINUTES
        );
//        return passengerMapper.getByUserId(userId);
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
        passenger.setVerifyStatus(VerifyStatusConstants.UNREVIEWED);

        // 设置变动时间
        passenger.setCreateTime(LocalDateTime.now());
        passenger.setUpdateTime(LocalDateTime.now());

        passengerMapper.insert(passenger);
    }

    /**
     * 更新乘车人信息
     * @param psgrUpdateDTO
     */
    /*@AutoClearAggCache(
            keySource = AutoClearAggCache.KeySource.THREAD_LOCAL,
            singleKeyPrefix = RedisConstants.RAIL_PASSENGER_LIST_USER_PREFIX
    )*/
    public void update(PsgrUpdateDTO psgrUpdateDTO) {
        Passenger passenger = BeanUtil.copyProperties(psgrUpdateDTO, Passenger.class);
        passenger.setUpdateTime(LocalDateTime.now());
        passengerMapper.updateById(passenger);

        String userId = ThreadLocalUtils.get();
        cacheClient.autoClearAggCache(RedisConstants.RAIL_PASSENGER_LIST_USER_PREFIX + userId);
        /*// 删除缓存
        String userId = ThreadLocalUtils.get();
        stringRedisTemplate.delete(RedisConstants.RAIL_PASSENGER_LIST_USER_PREFIX + userId);*/
    }

    /**
     * 根据ids移除乘车人
     * @param ids
     */
    /*@AutoClearAggCache(
            keySource = AutoClearAggCache.KeySource.THREAD_LOCAL,
            singleKeyPrefix = RedisConstants.RAIL_PASSENGER_LIST_USER_PREFIX
    )*/
    public void deleteByIds(List<Long> ids) {
        passengerMapper.batchDelete(ids);

        String userId = ThreadLocalUtils.get();
        cacheClient.autoClearAggCache(RedisConstants.RAIL_PASSENGER_LIST_USER_PREFIX + userId);
        // 删除缓存
        /*String userId = ThreadLocalUtils.get();
        stringRedisTemplate.delete(RedisConstants.RAIL_PASSENGER_LIST_USER_PREFIX + userId);*/
    }
}
