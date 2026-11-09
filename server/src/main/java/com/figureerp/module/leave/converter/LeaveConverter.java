package com.figureerp.module.leave.converter;

import com.figureerp.common.constant.ApplyStatus;
import com.figureerp.common.constant.UserRole;
import com.figureerp.module.leave.entity.LeaveApply;
import com.figureerp.module.leave.vo.LeaveVO;

/**
 * 请假单实体 → 出参。
 * <p>
 * 只做纯字段映射；can* 权限开关依赖当前登录用户，由 {@code LeaveService} 在转换后补上。
 */
public final class LeaveConverter {

    private LeaveConverter() {
    }

    public static LeaveVO toVO(LeaveApply e) {
        LeaveVO vo = new LeaveVO();
        vo.setId(e.getId());
        vo.setApplyNo(e.getApplyNo());
        vo.setUserId(e.getUserId());
        vo.setUserName(e.getUserName());
        vo.setRole(e.getRole());
        vo.setRoleLabel(UserRole.labelOf(e.getRole()));
        vo.setLeaveType(e.getLeaveType());
        vo.setLeaveTypeText(LeaveType.labelOf(e.getLeaveType()));
        vo.setStartTime(e.getStartTime());
        vo.setEndTime(e.getEndTime());
        vo.setDurationDays(e.getDurationDays());
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
