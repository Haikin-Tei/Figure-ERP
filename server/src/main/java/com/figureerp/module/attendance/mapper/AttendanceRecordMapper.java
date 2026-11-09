package com.figureerp.module.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.figureerp.module.attendance.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工考勤记录 Mapper。
 * <p>
 * 列表 / 统计都是单表条件查询，不需要自定义 XML。
 */
@Mapper
public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecord> {
}
