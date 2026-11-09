package com.figureerp.module.overtime.converter;

import com.figureerp.common.constant.ApplyStatus;
import com.figureerp.common.constant.UserRole;
import com.figureerp.module.overtime.entity.OvertimeApply;
import com.figureerp.module.overtime.vo.OvertimeVO;

/**
 * 加班单实体 → 出参。
 * <p>
 * 只做纯字段映射；can* 权限开关依赖当前登录用户，由 {@code OvertimeService} 在转换后补上。
 */
public final class OvertimeConverter {

    private OvertimeConverter() {
    }

    public static OvertimeVO toVO(OvertimeApply e) {
        OvertimeVO vo = new OvertimeVO();
        vo.setId(e.getId());
        vo.setApplyNo(e.getApplyNo());
        vo.setUserId(e.getUserId());
        vo.setUserName(e.getUserName());
        vo.setRole(e.getRole());
        vo.setRoleLabel(UserRole.labelOf(e.getRole()));
        vo.setOvertimeType(e.getOvertimeType());
        vo.setOvertimeTypeText(OvertimeType.labelOf(e.getOvertimeType()));
        vo.setStartTime(e.getStartTime());
        vo.setEndTime(e.getEndTime());
        vo.setDurationHours(e.getDurationHours());
        vo.setReason(e.getReason());
        vo.setStatus(e.getStatus());
        vo.setStatusText(ApplyStatus.labelOf(e.getStatus()));
        vo.setApproverName(e.getApproverName());
        vo.setApproveTime(e.getApproveTime());
        vo.setApproveRemark(e.getApproveRemark());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
