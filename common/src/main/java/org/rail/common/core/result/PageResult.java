package org.rail.common.core.result;

import com.github.pagehelper.Page;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页结果VO
 * @param <T> 泛型，适配不同业务的数据类型
 */
@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 总条数
     */
    private Long total = 0L;

    /**
     * 分页数据列表
     */
    private List<T> records;

    /**
     * 总页数
     */
    private Integer pages = 0;

    /**
     * 构造方法：自动计算总页数（适配PageHelper的Page类型）
     */
    public PageResult(List<T> list) {
        if(list instanceof Page) {
            Page<T> page = (Page<T>) list;
            this.total = page.getTotal(); // 总条数
            this.records = page; // 当前页数据
            this.pages = page.getPages(); // 总页数
        }
    }

    /**
     * 构造方法2：适配内存分页/手动分页（核心：传入总条数+当前页数据，自动计算总页数）
     * @param total 总条数
     * @param records 当前页数据
     * @param pageSize 每页条数（用于计算总页数）
     */
    public PageResult(Long total, List<T> records, Integer pageSize) {
        // 处理空值，避免NPE
        this.total = total == null ? 0L : total;
        this.records = records == null ? Collections.emptyList() : records;
        // 自动计算总页数（向上取整：比如总条数15，页大小10，总页数2）
        this.pages = this.total == 0L ? 0 : (int) Math.ceil((double) this.total / pageSize);
    }

}
