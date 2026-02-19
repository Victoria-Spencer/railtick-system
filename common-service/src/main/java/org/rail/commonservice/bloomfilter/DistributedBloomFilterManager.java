package org.rail.commonservice.bloomfilter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 分布式布隆过滤器管理器（基于RedisBloom）
 * 适配聚合缓存的防穿透场景，支持多节点数据共享
 */
@Slf4j
@Component
public class DistributedBloomFilterManager {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${cache.bloom.expected-insertions:1000000}")
    private long expectedInsertions;

    @Value("${cache.bloom.fpp:0.001}")
    private double fpp;

    @Value("${cache.bloom.prefix:bloom:}")
    private String bloomPrefix;

    public void initBloomFilter(String bizType) {
        if (bizType == null || bizType.isBlank()) {
            log.warn("布隆过滤器初始化失败：业务类型为空");
            return;
        }
        String bloomKey = buildBloomKey(bizType);

        Boolean exists = stringRedisTemplate.hasKey(bloomKey);
        if (Boolean.TRUE.equals(exists)) {
            log.debug("布隆过滤器已存在，无需重复初始化 | BizType:{}", bizType);
            return;
        }

        try {
            stringRedisTemplate.execute((RedisCallback<Object>) connection -> {
                // 修正：命令名直接传String，参数传byte[]
                connection.execute(
                        "BF.RESERVE", // 命令名：String
                        bloomKey.getBytes(StandardCharsets.UTF_8),
                        String.valueOf(fpp).getBytes(StandardCharsets.UTF_8),
                        String.valueOf(expectedInsertions).getBytes(StandardCharsets.UTF_8)
                );
                return null;
            });
            log.info("分布式布隆过滤器初始化成功 | BizType:{}, 预期插入量:{}, 误判率:{}",
                    bizType, expectedInsertions, fpp);
        } catch (Exception e) {
            log.warn("RedisBloom模块未加载（BF.RESERVE指令失败），将在首次添加元素时自动创建过滤器 | BizType:{}", bizType);
            log.debug("初始化失败详情", e);
        }
    }

    public void add(String bizType, String key) {
        if (bizType == null || bizType.isBlank() || key == null || key.isBlank()) {
            log.warn("布隆过滤器添加元素失败：参数为空 | BizType:{}, Key:{}", bizType, key);
            return;
        }
        String bloomKey = buildBloomKey(bizType);

        try {
            Boolean success = stringRedisTemplate.execute((RedisCallback<Boolean>) connection -> {
                // 修正：命令名直接传String
                Long result = (Long) connection.execute(
                        "BF.ADD", // 命令名：String
                        bloomKey.getBytes(StandardCharsets.UTF_8),
                        key.getBytes(StandardCharsets.UTF_8)
                );
                return result != null && result == 1;
            });
            log.debug("布隆过滤器添加元素 | BizType:{}, Key:{}, 结果:{}",
                    bizType, key, Boolean.TRUE.equals(success) ? "成功" : "已存在");
        } catch (Exception e) {
            log.error("布隆过滤器添加元素失败 | BizType:{}, Key:{}", bizType, key, e);
        }
    }

    public void addBatch(String bizType, List<String> keys) {
        if (bizType == null || bizType.isBlank() || keys == null || keys.isEmpty()) {
            log.warn("布隆过滤器批量添加失败：参数为空 | BizType:{}, Key数量:{}", bizType, keys.size());
            return;
        }
        String bloomKey = buildBloomKey(bizType);

        try {
            stringRedisTemplate.execute((RedisCallback<Object>) connection -> {
                byte[][] args = new byte[keys.size() + 1][];
                args[0] = bloomKey.getBytes(StandardCharsets.UTF_8);
                for (int i = 0; i < keys.size(); i++) {
                    args[i + 1] = keys.get(i).getBytes(StandardCharsets.UTF_8);
                }
                // 修正：命令名直接传String
                connection.execute("BF.MADD", args);
                return null;
            });
            log.debug("布隆过滤器批量添加成功 | BizType:{}, Key数量:{}", bizType, keys.size());
        } catch (Exception e) {
            log.error("布隆过滤器批量添加失败 | BizType:{}, Key数量:{}", bizType, keys.size(), e);
        }
    }

    public boolean mightContain(String bizType, String key) {
        if (bizType == null || bizType.isBlank() || key == null || key.isBlank()) {
            log.warn("布隆过滤器判断失败：参数为空 | BizType:{}, Key:{}", bizType, key);
            return false;
        }
        String bloomKey = buildBloomKey(bizType);

        try {
            Boolean exists = stringRedisTemplate.execute((RedisCallback<Boolean>) connection -> {
                // 修正：命令名直接传String
                Long result = (Long) connection.execute(
                        "BF.EXISTS", // 命令名：String
                        bloomKey.getBytes(StandardCharsets.UTF_8),
                        key.getBytes(StandardCharsets.UTF_8)
                );
                return result != null && result == 1;
            });
            boolean mightContain = Boolean.TRUE.equals(exists);
            log.debug("布隆过滤器判断结果 | BizType:{}, Key:{}, 可能存在:{}", bizType, key, mightContain);
            return mightContain;
        } catch (Exception e) {
            log.error("布隆过滤器判断失败 | BizType:{}, Key:{}", bizType, key, e);
            return true;
        }
    }

    public void deleteBloomFilter(String bizType) {
        if (bizType == null || bizType.isBlank()) {
            log.warn("布隆过滤器删除失败：业务类型为空");
            return;
        }
        String bloomKey = buildBloomKey(bizType);
        Boolean deleted = stringRedisTemplate.delete(bloomKey);
        log.info("布隆过滤器删除结果 | BizType:{}, 结果:{}",
                bizType, Boolean.TRUE.equals(deleted) ? "成功" : "不存在");
    }

    private String buildBloomKey(String bizType) {
        return bloomPrefix + bizType;
    }
}