package com.figureerp.module.overtime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.figureerp.module.overtime.entity.OvertimeApply;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工加班申请 Mapper。
 */
@Mapper
public interface OvertimeApplyMapper extends BaseMapper<OvertimeApply> {
}
