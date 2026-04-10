package org.rail.ticketservice.task;

import lombok.extern.slf4j.Slf4j;
import org.rail.common.core.exception.CacheInitException;
import org.rail.ticketservice.service.SeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 座位缓存初始化任务
 * 服务启动时：加载所有列车座位信息到 Redis（info、group、bitmap）
 */
@Slf4j
@Component
public class SeatCacheInitRunner implements CommandLineRunner {

    @Autowired
    private SeatService seatService;

    /**
     * 服务启动后自动执行
     */
    @Override
    public void run(String... args) {
        log.info("开始初始化列车座位Redis缓存...");
        try {
            // 初始化座位基础信息 (Hash)
            seatService.initAllTrainSeatCache();

            // 初始化占用区间 (Bitmap)
            seatService.initAllSeatOccupancyBitmap();
            log.info("列车座位Redis缓存初始化完成");
        } catch (Exception e) {
            log.error("列车座位Redis缓存初始化失败", e);
            throw new CacheInitException("列车座位Redis缓存初始化失败", e);
        }
    }
}