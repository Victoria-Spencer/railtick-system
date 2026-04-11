package org.rail.ticketservice.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.rail.ticketservice.pojo.dto.TrainStopStationCacheDTO;
import org.rail.ticketservice.pojo.entity.Station;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 本地缓存统一配置中心
 */
@Configuration
public class LocalCacheConfig {

    // ====================== 站点本地缓存配置 ======================
    @Value("${station.cache.caffeine.max-size:10000}")
    private int stationCacheMaxSize;

    @Value("${station.cache.caffeine.refresh-hours:1}")
    private int stationCacheRefreshHours;

    /**
     * 站点全局本地缓存 Bean
     */
    @Bean
    public Cache<String, List<Station>> stationLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(stationCacheMaxSize)
                .expireAfterWrite(stationCacheRefreshHours, TimeUnit.HOURS)
                .build();
    }

    // ===================== 列车经停站缓存配置 =====================
    @Value("${train.stop.cache.caffeine.max-size:10000}")
    private int trainStopCacheMaxSize;
    @Value("${train.stop.cache.caffeine.refresh-hours:24}")
    private int trainStopCacheRefreshHours;

    /**
     * 列车经停站全局本地缓存 Bean
     */
    @Bean
    public Cache<Long, TrainStopStationCacheDTO> trainStopStationLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(trainStopCacheMaxSize)
                .expireAfterWrite(trainStopCacheRefreshHours, TimeUnit.HOURS)
                .build();
    }
}
