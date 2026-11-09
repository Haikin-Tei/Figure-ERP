package com.figureerp.module.leave.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.figureerp.module.leave.entity.LeaveApply;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工请假申请 Mapper。
 */
@Mapper
public interface LeaveApplyMapper extends BaseMapper<LeaveApply> {
}
