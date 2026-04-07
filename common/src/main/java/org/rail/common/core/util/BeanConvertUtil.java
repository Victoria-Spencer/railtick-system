package org.rail.common.core.util;

import org.springframework.beans.BeanUtils;
import java.util.ArrayList;
import java.util.List;

public class BeanConvertUtil {

    /**
     * 批量拷贝：公共字段 + 子列表 → 目标DTO列表
     * @param commonSource 公共字段对象
     * @param subList 子列表
     * @param targetClass 目标DTO类型
     * @return 合并后的列表
     */
    public static <S, T, C> List<T> copyWithCommonField(
            C commonSource,
            List<S> subList,
            Class<T> targetClass
    ) {
        List<T> targetList = new ArrayList<>();
        for (S sub : subList) {
            try {
                T target = targetClass.getDeclaredConstructor().newInstance();
                // 拷贝子对象字段
                BeanUtils.copyProperties(sub, target);
                // 拷贝公共字段
                BeanUtils.copyProperties(commonSource, target);
                targetList.add(target);
            } catch (Exception e) {
                throw new RuntimeException("对象拷贝失败", e);
            }
        }
        return targetList;
    }
}