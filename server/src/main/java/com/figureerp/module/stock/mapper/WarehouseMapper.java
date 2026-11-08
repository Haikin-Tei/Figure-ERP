package com.figureerp.module.stock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.figureerp.module.stock.entity.Warehouse;
import org.apache.ibatis.annotations.Mapper;

/** 仓库表 Mapper */
@Mapper
public interface WarehouseMapper extends BaseMapper<Warehouse> {
}
