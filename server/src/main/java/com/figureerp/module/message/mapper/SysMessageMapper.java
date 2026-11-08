package com.figureerp.module.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.figureerp.module.message.entity.SysMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 个人消息 Mapper，只用到 BaseMapper 的通用 CRUD。
 */
@Mapper
public interface SysMessageMapper extends BaseMapper<SysMessage> {
}
