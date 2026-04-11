package org.rail.ticketservice.task;

import lombok.extern.slf4j.Slf4j;
import org.rail.common.core.exception.CacheInitException;
import org.rail.common.redis.constant.RedisConstants;
import org.rail.ticketservice.service.SeatService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 座位缓存初始化任务
 * 服务启动时：加载所有列车座位信息到 Redis（info、group、bitmap）
 */
@Slf4j
@Component
public class SeatCacheInitRunner implements CommandLineRunner {

    private static final String SEAT_CACHE_INIT_LOCK = RedisConstants.SEAT_CACHE_INIT_LOCK;

    @Autowired
    private SeatService seatService;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 服务启动后自动执行
     */
    @Override
    public void run(String... args) {
        log.info("开始初始化列车座位Redis缓存...");

        RLock lock = redissonClient.getLock(SEAT_CACHE_INIT_LOCK);
        try {
            boolean locked = lock.tryLock(3, 30, TimeUnit.SECONDS);
            if (locked) {
                // 初始化座位基础信息 (Hash)
                seatService.initAllTrainSeatCache();
                // 初始化占用区间 (Bitmap)
                seatService.initAllSeatOccupancyBitmap();
                log.info("列车座位Redis缓存初始化完成");
            } else {
                log.info("其他实例开始座位缓存初始化，当前实例跳过执行");
            }
        } catch (InterruptedException e) {
            log.error("座位缓存初始化分布式锁获取中断", e);
            Thread.currentThread().interrupt();
            throw new CacheInitException("座位缓存初始化失败", e);
        } catch (Exception e) {
            log.error("列车座位Redis缓存初始化失败", e);
            throw new CacheInitException("列车座位Redis缓存初始化失败", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}