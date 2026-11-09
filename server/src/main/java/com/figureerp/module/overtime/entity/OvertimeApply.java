package com.figureerp.module.overtime.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 员工加班申请。
 * <p>
 * 结构与请假申请一致（同样走「待审批 → 通过 / 驳回 / 撤销」），差别只有两点：
 * 类型是加班类型（工作日 / 休息日 / 节假日），时长单位是小时。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("overtime_apply")
public class OvertimeApply extends BaseEntity {

    /** 加班单号：OT + yyyyMMdd + 4 位序号 */
    private String applyNo;

    /** 申请人 sys_user.id */
    private Long userId;

    /** 申请人姓名快照 */
    private String userName;

    /** 申请人角色编码快照 */
    private String role;

    /** 加班类型：W 工作日 / O 休息日 / H 节假日 */
    private String overtimeType;

    /** 加班开始时间 */
    private LocalDateTime startTime;

    /** 加班结束时间 */
    private LocalDateTime endTime;

    /** 加班时长（小时），后端按起止时间计算，保留 1 位小数 */
    private BigDecimal durationHours;

    /** 加班事由 */
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
