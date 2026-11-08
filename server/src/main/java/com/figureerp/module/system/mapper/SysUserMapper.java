package com.figureerp.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.figureerp.module.system.entity.SysUser;

/**
 * 用户表 Mapper。
 * <p>
 * 继承 BaseMapper 后，单表增删改查与条件构造器都是现成的，
 * 只有多表关联、复杂统计才需要在 resources/mapper 下写 XML。
 */
public interface SysUserMapper extends BaseMapper<SysUser> {
}
