package com.figureerp.module.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.figureerp.module.stock.entity.InboundOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 入库单 Mapper，常规 CRUD 由 MyBatis-Plus 提供。
 */
@Mapper
public interface InboundOrderMapper extends BaseMapper<InboundOrder> {
}
