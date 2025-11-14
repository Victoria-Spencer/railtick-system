package org.rail.commonservice.result;

import com.github.pagehelper.Page;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页结果VO
 * @param <T> 泛型，适配不同业务的数据类型
 */
@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    /**
     * 总条数
     */
    private Long total;

    /**
     * 分页数据列表
     */
    private List<T> records;

    /**
     * 总页数
     */
    private Integer pages;

    /**
     * 构造方法：自动计算总页数
     */
    public PageResult(List<T> list) {
        if(list instanceof Page) {
            Page<T> page = (Page<T>) list;
            this.total = page.getTotal(); // 总条数
            this.records = page; // 当前页数据
            this.pages = page.getPages(); // 总页数
        }
    }
}
