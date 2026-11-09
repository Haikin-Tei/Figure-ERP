package com.figureerp.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工考勤记录：一行 = 一名员工某一天的考勤。
 * <p>
 * 录入方式是手动填报，所以下班的「未打卡」是有意义的状态：
 * 非销售岗留空记 M（未打卡），销售岗留空记 X（免打卡）。
 * <p>
 * {@code userId} / {@code userName} / {@code role} 都是快照：员工调岗或账号被删后，
 * 历史考勤仍要能显示当时是谁、什么角色（销售免下班卡就是按 role 判断的）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("attendance_record")
public class AttendanceRecord extends BaseEntity {

    /** 员工 sys_user.id */
    private Long userId;

    /** 员工姓名快照 */
    private String userName;

    /** 员工角色编码快照，用于判断销售岗免下班卡 */
    private String role;

    /** 考勤日期 */
    private LocalDate attendDate;

    /** 上班时间（手动填报），必填 */
    private LocalDateTime checkInTime;

    /**
     * 下班时间，销售岗为空。
     * <p>
     * 标 ALWAYS：编辑时把已有下班时间清空（改成「未打卡」）必须能真正写 NULL，
     * 否则 MyBatis-Plus 默认跳过 null 字段，改完还是旧值。
     * 注意配套：更新一律走 {@code LambdaUpdateWrapper} 逐列 set，不要用空实体 updateById，
     * 否则 ALWAYS 会把没填的列一起写成 NULL。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime checkOutTime;

    /** 上班状态：N 正常 / L 迟到 */
    private String checkInStatus;

    /** 下班状态：N 正常 / E 早退 / M 未打卡 / X 免打卡 */
    private String checkOutStatus;

    /** 当日工时（分钟），下班时间为空时为空 */
    private Integer workMinutes;

    /** 备注 */
    private String remark;
}
