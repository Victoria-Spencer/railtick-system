package org.rail.commonservice.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 聚合缓存结果包装类
 * 同时包含：聚合数据 + 依赖的单表Key列表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AggCacheResult<D> {
    /**
     * 最终聚合数据（返回给业务层）
     */
    private D data;

    /**
     * 依赖的单表Key列表（用于构建dep Set）
     */
    private List<String> dependSingleKeys;

    /**
     * 静态工厂方法：快速创建（无依赖单表Key时用）
     */
    public static <D> AggCacheResult<D> of(D data) {
        return new AggCacheResult<>(data, Collections.emptyList());
    }

    /**
     * 静态工厂方法：快速创建（有依赖单表Key时用）
     */
    public static <D> AggCacheResult<D> of(D data, List<String> dependSingleKeys) {
        return new AggCacheResult<>(data, dependSingleKeys);
    }
}