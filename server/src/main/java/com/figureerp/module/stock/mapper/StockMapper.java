package com.figureerp.module.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.figureerp.module.stock.entity.Stock;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存表 Mapper。
 * <p>
 * 页面的「按手办汇总」用 {@code QueryWrapper} 的 groupBy + selectMaps 实现，
 * 不写 XML / 注解 SQL，和其他模块保持一致。
 */
@Mapper
public interface StockMapper extends BaseMapper<Stock> {
}
