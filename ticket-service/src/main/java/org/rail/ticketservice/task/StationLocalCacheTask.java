package org.rail.ticketservice.task;

import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.rail.common.redis.exception.CacheInitException;
import org.rail.ticketservice.mapper.StationMapper;
import org.rail.ticketservice.pojo.entity.Station;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 站点本地缓存管理器（初始化+定时刷新+分布式开关）
 */
@Slf4j
@Component
public class StationLocalCacheTask {

    private static final String SCHEDULE_RATE_HOURS = "${station.cache.schedule-rate-hours:24}";

    @Value("${station.cache.schedule.enable:true}")
    private boolean scheduleEnable; // 定时任务开关

    @Autowired
    private Cache<String, List<Station>> stationLocalCache;

    @Autowired
    private StationMapper stationMapper;

    /**
     * 项目启动初始化缓存
     */
    @PostConstruct
    public void initStationCache() {
        log.info("开始初始化站点本地缓存...");
        try {
            stationLocalCache.put("all", loadAllStationsFromDb());
            log.info("站点本地缓存初始化完成");
        } catch (Exception e) {
            log.error("站点本地缓存初始化失败", e);
            throw new CacheInitException("站点缓存初始化失败", e);
        }
    }

    /**
     * 定时刷新缓存（分布式开关控制）
     */
    @Scheduled(fixedRateString = SCHEDULE_RATE_HOURS, timeUnit = TimeUnit.HOURS)
    public void refreshStationCache() {
        if (!scheduleEnable) {
            log.debug("当前实例定时任务开关关闭，跳过站点缓存刷新");
            return;
        }

        log.info("刷新站点本地缓存");
        try {
            stationLocalCache.put("all", loadAllStationsFromDb());
            log.info("站点本地缓存定时刷新完成");
        } catch (Exception e) {
            log.error("站点本地缓存定时刷新失败", e);
            // 可选：发送告警（如钉钉/短信）
        }
    }

    /**
     * 从数据库全量加载站点数据
     * @return 站点列表
     */
    private List<Station> loadAllStationsFromDb() {
        try {
            return stationMapper.selectAllStations();
        } catch (Exception e) {
            log.error("从数据库加载站点数据失败", e);
            throw new CacheInitException("加载站点数据失败", e);
        }
    }

    /**
     * 对外提供缓存数据（带降级）
     * @return 站点列表
     */
    public List<Station> getAllStations() {
        try {
            List<Station> cache = stationLocalCache.getIfPresent("all");
            if (cache != null) {
                return cache;
            }

            // 重建缓存
            List<Station> stations = loadAllStationsFromDb();
            stationLocalCache.put("all", stations);
            return stations;
        } catch (Exception e) {
            log.error("获取站点本地缓存失败，降级查询数据库", e);
            // 降级策略：直接查库
            return loadAllStationsFromDb();
        }
    }
}