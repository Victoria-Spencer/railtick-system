package org.rail.orderservice.utils;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 雪花算法工具类（全局唯一ID生成器）
 * 注意：通过@Component注册为Spring Bean，确保配置注入生效
 */
@Component // 关键：注册为Spring Bean，使@Value和@PostConstruct生效
public class SnowflakeIdGenerator {

    // ============================== 基础配置 ==============================
    private static final long START_TIMESTAMP = 1714502400000L; // 2024-05-01 00:00:00

    private static final int TIMESTAMP_BIT = 41;
    private static final int DATA_CENTER_BIT = 5;
    private static final int MACHINE_BIT = 5;
    private static final int SEQUENCE_BIT = 12;

    // ============================== 最大值计算 ==============================
    private static final long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_BIT);
    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_BIT);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BIT);

    // ============================== 移位偏移量 ==============================
    private static final int MACHINE_SHIFT = SEQUENCE_BIT;
    private static final int DATA_CENTER_SHIFT = SEQUENCE_BIT + MACHINE_BIT;
    private static final int TIMESTAMP_SHIFT = SEQUENCE_BIT + MACHINE_BIT + DATA_CENTER_BIT;

    // ============================== 静态变量（核心修正）==============================
    // 1. 改为静态变量，允许静态方法访问
    private static long dataCenterId;
    private static long machineId;
    private static long sequence = 0L;
    private static long lastTimestamp = -1L;

    // 2. 非静态变量，用于接收Spring注入（注入后通过init方法赋值给静态变量）
    @Value("${snowflake.data-center-id:1}")
    private long injectDataCenterId; // 临时接收注入值
    @Value("${snowflake.machine-id:2}")
    private long injectMachineId;     // 临时接收注入值

    // 线程安全锁
    private static final ReentrantLock lock = new ReentrantLock();

    // ============================== 初始化（核心修正）==============================
    /**
     * Spring初始化后执行：将注入的非静态变量赋值给静态变量
     */
    @PostConstruct
    public void init() {
        // 用注入的临时值初始化静态变量
        dataCenterId = injectDataCenterId;
        machineId = injectMachineId;
        validateConfig(); // 校验配置
    }

    /**
     * 私有化构造方法：禁止外部实例化（但Spring可以创建实例）
     */
    private SnowflakeIdGenerator() {}

    // ============================== 配置校验 ==============================
    private static void validateConfig() {
        if (dataCenterId < 0 || dataCenterId > MAX_DATA_CENTER_ID) {
            throw new IllegalStateException("数据中心ID必须在0~" + MAX_DATA_CENTER_ID + "范围内");
        }
        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new IllegalStateException("机器ID必须在0~" + MAX_MACHINE_ID + "范围内");
        }
    }

    // ============================== 核心生成方法 ==============================
    public static long nextId() {
        lock.lock();
        try {
            long currentTimestamp = System.currentTimeMillis();

            if (currentTimestamp < lastTimestamp) {
                long waitMs = lastTimestamp - currentTimestamp;
                if (waitMs > 1000) {
                    throw new RuntimeException("时钟回拨异常：" + waitMs + "ms");
                }
                TimeUnit.MILLISECONDS.sleep(waitMs);
                currentTimestamp = System.currentTimeMillis();
            }

            if (currentTimestamp == lastTimestamp) {
                sequence = (sequence + 1) & MAX_SEQUENCE;
                if (sequence == 0) {
                    currentTimestamp = nextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }

            lastTimestamp = currentTimestamp;

            return (
                    ((currentTimestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT) |
                            (dataCenterId << DATA_CENTER_SHIFT) |
                            (machineId << MACHINE_SHIFT) |
                            sequence
            );
        } catch (InterruptedException e) {
            throw new RuntimeException("生成ID失败", e);
        } finally {
            lock.unlock();
        }
    }

    private static long nextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    // ============================== 业务方法 ==============================
    public static String generatePreOrderSn() {
        return String.valueOf(nextId());
    }

    public static String generateOrderSn() {
        return "ORD" + nextId();
    }
}