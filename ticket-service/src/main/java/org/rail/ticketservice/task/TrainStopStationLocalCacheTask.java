package org.rail.ticketservice.task;

import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.rail.common.core.exception.BusinessException;
import org.rail.common.redis.exception.CacheInitException;
import org.rail.ticketservice.mapper.TrainStopStationMapper;
import org.rail.ticketservice.model.dto.TrainStopStationCacheDTO;
import org.rail.ticketservice.model.entity.TrainStopStation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 列车经停站本地缓存任务
 */
@Slf4j
@Component
public class TrainStopStationLocalCacheTask {

    private static final String SCHEDULE_RATE_HOURS = "${train.stop.cache.schedule-rate-hours:24}";

    @Value("${train.stop.cache.schedule.enable:true}")
    private boolean scheduleEnable;

    @Autowired
    private Cache<Long, TrainStopStationCacheDTO> trainStopStationLocalCache;
    @Autowired
    private TrainStopStationMapper trainStopStationMapper;

    @PostConstruct
    public void initTrainStopCache() {
        log.info("开始初始化列车经停站本地缓存...");
        try {
            loadAllStopStationsToCache();
            log.info("列车经停站缓存初始化完成");
        } catch (Exception e) {
            log.error("列车经停站缓存初始化失败", e);
            throw new CacheInitException("经停站缓存初始化失败", e);
        }
    }

    @Scheduled(fixedRateString = SCHEDULE_RATE_HOURS, timeUnit = TimeUnit.HOURS)
    public void refreshTrainStopCache() {
        if (!scheduleEnable) {
            log.debug("经停站缓存定时任务开关关闭，跳过刷新");
            return;
        }

        log.info("开始刷新列车经停站本地缓存");
        try {
            loadAllStopStationsToCache();
        } catch (Exception e) {
            log.error("列车经停站缓存刷新失败", e);
        }
    }

    /**
     * 加载所有列车经停站数据到本地缓存
     */
    private void loadAllStopStationsToCache() {
        List<TrainStopStation> allStopList = trainStopStationMapper.selectAll();
        if (CollectionUtils.isEmpty(allStopList)) {
            return;
        }

        // 按 trainId 分组
        Map<Long, List<TrainStopStation>> trainGroupMap = allStopList.stream()
                .collect(Collectors.groupingBy(TrainStopStation::getTrainId));

        // 批量构建缓存
        trainGroupMap.forEach((trainId, stopList) -> {
            stopList.sort(Comparator.comparing(TrainStopStation::getSequence));
            TrainStopStationCacheDTO cacheDTO = buildCacheDTO(stopList);
            trainStopStationLocalCache.put(trainId, cacheDTO);
        });
    }

    /**
     * 根据 trainId 获取经停站缓存，若未命中则临时加载
     */
    public TrainStopStationCacheDTO getCacheByTrainId(Long trainId) {
        if (trainId == null) {
            throw new BusinessException("列车ID不能为空");
        }
        try {
            TrainStopStationCacheDTO cache = trainStopStationLocalCache.getIfPresent(trainId);
            if (cache != null) {
                return cache;
            }

            List<TrainStopStation> stopList = loadCacheByTrainId(trainId);
            TrainStopStationCacheDTO cacheDTO = buildCacheDTO(stopList);
            trainStopStationLocalCache.put(trainId, cacheDTO);

            return cacheDTO;
        } catch (Exception e) {
            log.error("列车{}经停站缓存读取异常，强制降级查询数据库", trainId, e);
            List<TrainStopStation> stopList = loadCacheByTrainId(trainId);
            return buildCacheDTO(stopList);
        }
    }


    /**
     * 获取终点站序号
     */
    public Integer getTrainTerminalSeq(Long trainId) {
        return getCacheByTrainId(trainId).getTerminalSeq();
    }

    /**
     * 根据 trainId 从数据库加载经停站数据
     */
    private List<TrainStopStation> loadCacheByTrainId(Long trainId) {
        try {
            return trainStopStationMapper.selectByTrainId(trainId);
        } catch (Exception e) {
            throw new BusinessException("查询列车经停站信息失败");
        }
    }

    /**
     * 构建经停站缓存DTO
     */
    private TrainStopStationCacheDTO buildCacheDTO(List<TrainStopStation> stopStationList) {
        if (CollectionUtils.isEmpty(stopStationList)) {
            return new TrainStopStationCacheDTO(Collections.emptyMap(), 0);
        }

        Map<Long, Integer> stationIdMap = stopStationList.stream()
                .collect(Collectors.toMap(
                        TrainStopStation::getStationId,
                        TrainStopStation::getSequence,
                        (oldVal, newVal) -> oldVal
                ));

        // 有序列表最后一个元素即为终点站
        Integer terminalSeq = stopStationList.getLast().getSequence();

        return new TrainStopStationCacheDTO(stationIdMap, terminalSeq);
    }
}