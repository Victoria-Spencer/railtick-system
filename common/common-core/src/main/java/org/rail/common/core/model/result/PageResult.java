package org.rail.common.core.model.result;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
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

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总条数 **/
    private Long total = 0L;

    /** 分页数据列表 **/
    private List<T> records;

    /** 总页数 **/
    private Integer pages = 0;

    /** 当前页码 **/
    private Integer pageNum = 1;

    /** 每页条数 **/
    private Integer pageSize = 10;

    /**
     * 适配内存分页/手动分页
     * @param total 总条数
     * @param records 当前页数据
     * @param pageNum 当前页码
     * @param pageSize 每页条数（用于计算总页数）
     */
    public PageResult(Long total, List<T> records, Integer pageNum, Integer pageSize) {
        // 处理空值，避免NPE
        this.total = total == null ? 0L : total;
        this.records = records == null ? Collections.emptyList() : records;
        this.pageNum = pageNum == null ? 1 : pageNum;
        this.pageSize = pageSize == null ? 10 : pageSize;
        // 自动计算总页数（向上取整：比如总条数15，页大小10，总页数2）
        this.pages = this.total == 0L ? 0 : (int) Math.ceil((double) this.total / this.pageSize);
    }
}
