package org.rail.common.core.util;

import org.springframework.beans.BeanUtils;

import java.lang.reflect.Field;
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

    /**
     * 【通用泛型】单个DTO列表 → 批量DTO（自动提取公共字段 + 自动封装子列表）
     * 适用所有：公共字段相同 + 独有字段组成列表 的批量DTO结构
     *
     * @param singleDtoList 单个DTO列表
     * @param batchDtoClass 批量DTO Class
     * @param subDtoClass   子项DTO Class
     * @param subListFieldName 批量DTO中 子列表的字段名
     * @return 组装好的 批量DTO对象
     */
    public static <T, B, S> B convertToBatchDto(
            List<T> singleDtoList,
            Class<B> batchDtoClass,
            Class<S> subDtoClass,
            String subListFieldName
    ) {
        // 1. 初始化批量DTO
        B batchDto;
        try {
            batchDto = batchDtoClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("批量DTO初始化失败", e);
        }

        // 2. 空列表直接返回空列表
        if (singleDtoList == null || singleDtoList.isEmpty()) {
            setSubListToBatchDto(batchDto, subListFieldName, new ArrayList<>());
            return batchDto;
        }

        // 3. 从第一个DTO拷贝【公共字段】到批量DTO
        T firstDto = singleDtoList.getFirst();
        BeanUtils.copyProperties(firstDto, batchDto);

        // 4. 遍历生成【子项DTO列表】
        List<S> subList = new ArrayList<>();
        for (T singleDto : singleDtoList) {
            try {
                S subDto = subDtoClass.getDeclaredConstructor().newInstance();
                BeanUtils.copyProperties(singleDto, subDto);
                subList.add(subDto);
            } catch (Exception e) {
                throw new RuntimeException("子项DTO拷贝失败", e);
            }
        }

        // 5. 反射将子列表设置到批量DTO中
        setSubListToBatchDto(batchDto, subListFieldName, subList);

        return batchDto;
    }

    /**
     * 反射：给批量DTO设置子列表字段
     */
    private static <B> void setSubListToBatchDto(B batchDto, String fieldName, List<?> subList) {
        try {
            Field field = batchDto.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(batchDto, subList);
        } catch (Exception e) {
            throw new RuntimeException("批量DTO设置子列表失败，字段名：" + fieldName, e);
        }
    }
}