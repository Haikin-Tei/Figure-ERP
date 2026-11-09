package com.figureerp.module.leave.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 员工请假申请。
 * <p>
 * 状态流转见 {@link com.figureerp.common.constant.ApplyStatus}：
 * P 待审批 → A 已通过 / R 已驳回；P 状态下本人可撤销 → C 已撤销。
 * <p>
 * {@code userName} / {@code role} 是申请人快照：员工改名或调岗后，
 * 历史单据仍要显示当时是谁、什么角色（审批时也按 role 判断层级）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("leave_apply")
public class LeaveApply extends BaseEntity {

    /** 请假单号：LV + yyyyMMdd + 4 位序号 */
    private String applyNo;

    /** 申请人 sys_user.id */
    private Long userId;

    /** 申请人姓名快照 */
    private String userName;

    /** 申请人角色编码快照 */
    private String role;

    /** 请假类型：A 事假 / B 病假 / C 年假 / D 调休 / E 婚假 / F 产假 / G 其他 */
    private String leaveType;

    /** 请假开始时间 */
    private LocalDateTime startTime;

    /** 请假结束时间 */
    private LocalDateTime endTime;

    /** 请假天数，后端按起止时间计算，不信任前端传值 */
    private BigDecimal durationDays;

    /** 请假事由 */
    private String reason;

    /** 状态：P 待审批 / A 已通过 / R 已驳回 / C 已撤销 */
    private String status;

    /** 审批人 sys_user.id */
    private Long approverId;

    /** 审批人姓名快照 */
    private String approverName;

    /** 审批时间 */
    private LocalDateTime approveTime;

    /** 审批意见 / 驳回原因 */
    private String approveRemark;
}
