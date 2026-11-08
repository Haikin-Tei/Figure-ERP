package com.figureerp.module.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.figureerp.module.stock.entity.InboundItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 入库单明细 Mapper。
 */
@Mapper
public interface InboundItemMapper extends BaseMapper<InboundItem> {
}
