package com.figureerp.module.overtime.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 加班单出参。
 * <p>
 * can* 四个开关由后端按「当前登录用户 + 单据状态」算好，前端只负责显示 / 隐藏按钮。
 */
@Data
@Schema(description = "加班单")
public class OvertimeVO {

    private Long id;

    @Schema(description = "加班单号")
    private String applyNo;

    @Schema(description = "申请人 ID")
    private Long userId;

    @Schema(description = "申请人姓名")
    private String userName;

    @Schema(description = "申请人角色编码")
    private String role;

    @Schema(description = "申请人角色文案")
    private String roleLabel;

    @Schema(description = "加班类型编码")
    private String overtimeType;

    @Schema(description = "加班类型文案")
    private String overtimeTypeText;

    @Schema(description = "加班开始时间")
    private LocalDateTime startTime;

    @Schema(description = "加班结束时间")
    private LocalDateTime endTime;

    @Schema(description = "加班时长（小时）")
    private BigDecimal durationHours;

    @Schema(description = "加班事由")
    private String reason;

    @Schema(description = "状态：P 待审批 / A 已通过 / R 已驳回 / C 已撤销")
    private String status;

    @Schema(description = "状态文案")
    private String statusText;

    @Schema(description = "审批人姓名")
    private String approverName;

    @Schema(description = "审批时间")
    private LocalDateTime approveTime;

    @Schema(description = "审批意见 / 驳回原因")
    private String approveRemark;

    @Schema(description = "当前用户是否可以审批")
    private Boolean canApprove;

    @Schema(description = "当前用户是否可以撤销（仅本人 + 待审批）")
    private Boolean canCancel;

    @Schema(description = "当前用户是否可以编辑")
    private Boolean canEdit;

    @Schema(description = "当前用户是否可以删除")
    private Boolean canDelete;

    @Schema(description = "提交时间")
    private LocalDateTime createTime;
}
