package org.rail.ticketservice.task;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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

    // ========== 配置注入 ==========
    @Value("${station.cache.schedule.enable:false}")
    private boolean scheduleEnable; // 定时任务开关
    @Value("${station.cache.caffeine.max-size:10000}")
    private int caffeineMaxSize; // 缓存最大容量
    @Value("${station.cache.caffeine.refresh-hours:1}")
    private int refreshHours; // 自动刷新时间
    @Value("${station.cache.schedule-rate-hours:1}")
    private long scheduleRateHours; // 定时任务执行频率

    // ========== 依赖注入 ==========
    @Autowired
    private StationMapper stationMapper;

    // ========== 本地缓存容器 ==========
    private LoadingCache<String, List<Station>> stationCache;

    // ========== 项目启动初始化缓存 ==========
    @PostConstruct
    public void initStationCache() {
        log.info("开始初始化站点本地缓存...");
        try {
            // 构建Caffeine缓存
            stationCache = Caffeine.newBuilder()
                    .maximumSize(caffeineMaxSize) // 最大容量
                    .refreshAfterWrite(refreshHours, TimeUnit.HOURS) // 写入后自动刷新
                    .build(key -> loadAllStationsFromDb()); // 缓存加载逻辑

            // 预加载缓存（首次启动主动加载，避免首次请求查库）
            stationCache.get("all");
            log.info("站点本地缓存初始化完成，缓存最大容量：{}，自动刷新时间：{}小时",
                    caffeineMaxSize, refreshHours);
        } catch (Exception e) {
            log.error("站点本地缓存初始化失败", e);
            throw new RuntimeException("站点缓存初始化失败，影响服务启动", e);
        }
    }

    // ========== 定时刷新缓存（分布式开关控制） ==========
    @Scheduled(fixedRateString = "${station.cache.schedule-rate-hours}", timeUnit = TimeUnit.HOURS)
    public void refreshStationCache() {
        // 1. 分布式开关：仅配置为true的实例执行
        if (!scheduleEnable) {
            log.debug("当前实例定时任务开关关闭，跳过站点缓存刷新");
            return;
        }

        // 2. 执行缓存刷新
        log.info("开始定时刷新站点本地缓存...");
        try {
            // 手动触发缓存刷新（异步执行，不阻塞主线程）
            stationCache.refresh("all");
            log.info("站点本地缓存定时刷新完成");
        } catch (Exception e) {
            log.error("站点本地缓存定时刷新失败", e);
            // 可选：发送告警（如钉钉/短信）
        }
    }

    // ========== 从数据库全量加载站点数据 ==========
    private List<Station> loadAllStationsFromDb() {
        log.info("从数据库全量加载站点数据...");
        try {
            List<Station> allStations = stationMapper.selectAllStations();
            log.info("从数据库加载站点数据完成，共{}条", allStations.size());
            return allStations;
        } catch (Exception e) {
            log.error("从数据库加载站点数据失败", e);
            throw new RuntimeException("加载站点数据失败", e);
        }
    }

    // ========== 对外提供缓存数据 ==========
    public List<Station> getAllStations() {
        try {
            return stationCache.get("all");
        } catch (Exception e) {
            log.error("获取站点本地缓存失败，降级查询数据库", e);
            // 降级策略：直接查库（避免缓存异常导致服务不可用）
            return loadAllStationsFromDb();
        }
    }
}