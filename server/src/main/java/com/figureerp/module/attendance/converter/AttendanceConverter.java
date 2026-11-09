package com.figureerp.module.attendance.converter;

import com.figureerp.common.constant.UserRole;
import com.figureerp.module.attendance.entity.AttendanceRecord;
import com.figureerp.module.attendance.vo.AttendanceVO;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 考勤实体 → 出参。
 * <p>
 * 唯一出口：状态文案、工时换算、销售免下班卡标记都只在这里算一次，
 * 页面、导出、验证脚本看到的永远是同一套口径。
 */
public final class AttendanceConverter {

    private AttendanceConverter() {
    }

    public static AttendanceVO toVO(AttendanceRecord r) {
        AttendanceVO vo = new AttendanceVO();
        vo.setId(r.getId());
        vo.setUserId(r.getUserId());
        vo.setUserName(r.getUserName());
        vo.setRole(r.getRole());
        vo.setRoleLabel(UserRole.labelOf(r.getRole()));
        vo.setAttendDate(r.getAttendDate());
        vo.setCheckInTime(r.getCheckInTime());
        vo.setCheckOutTime(r.getCheckOutTime());
        vo.setCheckInStatus(r.getCheckInStatus());
        vo.setCheckInStatusText(AttendanceStatus.inLabel(r.getCheckInStatus()));
        vo.setCheckOutStatus(r.getCheckOutStatus());
        vo.setCheckOutStatusText(AttendanceStatus.outLabel(r.getCheckOutStatus()));
        vo.setWorkMinutes(r.getWorkMinutes());
        if (r.getWorkMinutes() != null) {
            vo.setWorkHours(BigDecimal.valueOf(r.getWorkMinutes())
                    .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP));
        }
        vo.setExemptCheckOut(UserRole.isSales(r.getRole()));
        vo.setRemark(r.getRemark());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }
}
