package com.figureerp.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

/**
 * 分页响应结构，与 MyBatis-Plus 的 IPage 直接对接。
 *
 * @param records 当前页数据
 * @param total   总条数
 * @param page    当前页码（从 1 开始）
 * @param size    每页条数
 * @param pages   总页数
 */
public record PageResult<T>(List<T> records, long total, long page, long size, long pages) {

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    /** 分页查询后需要把 Entity 转成 VO 时用这个，避免在 Controller 里手写映射。 */
    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> converter) {
        return new PageResult<>(
                page.getRecords().stream().map(converter).toList(),
                page.getTotal(), page.getCurrent(), page.getSize(), page.getPages());
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(List.of(), 0, page, size, 0);
    }
}
