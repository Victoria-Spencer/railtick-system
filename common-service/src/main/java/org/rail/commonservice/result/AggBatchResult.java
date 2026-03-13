package org.rail.commonservice.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 批量聚合缓存查询的DB返回结果封装类
 * @param <D> 单个聚合数据的类型（如SeatClassVO、TrainDetailVO等）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AggBatchResult<D> {
    /**
     * Key-数据映射：Key为聚合缓存Key（由keyGenerator生成），Value为对应DTO的查库结果
     * 例：{"rail:seat:1001:1:5": SeatClassVO1, "rail:seat:1002:2:6": SeatClassVO2}
     */
    private Map<String, D> dataMap;

    /**
     * 所有依赖的单表Key列表（和单查询AggCacheResult的dependSingleKeys逻辑一致）
     * 用于记录聚合缓存和单表缓存的依赖关系，单表数据变更时可失效关联的聚合缓存
     * 例：["rail:seat:class:1", "rail:train:1001"]
     */
    private List<String> dependSingleKeys;

    /**
     * 静态工厂方法：快速创建（无依赖单表Key时用）
     */
    public static <D> AggBatchResult<D> of(Map<String, D> dataMap) {
        return new AggBatchResult<>(dataMap, Collections.emptyList());
    }

    /**
     * 静态工厂方法：快速创建（有依赖单表Key时用）
     */
    public static <D> AggBatchResult<D> of(Map<String, D> dataMap, List<String> dependSingleKeys) {
        return new AggBatchResult<>(dataMap, dependSingleKeys);
    }
}