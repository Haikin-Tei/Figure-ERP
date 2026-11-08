package com.figureerp.module.work.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 待办出参。
 * <p>
 * 前端只需要 status 里的 P/D 做勾选态、priorityText 做标签文案，
 * 「是否完成 / 是否逾期」由后端算好，页面不再重复判断。
 */
@Data
@Schema(description = "待办")
public class TodoVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "待办标题")
    private String title;

    @Schema(description = "补充说明，可空")
    private String content;

    @Schema(description = "计划日期", example = "2026-11-07")
    private LocalDate planDate;

    @Schema(description = "优先级：H 高 / N 普通 / L 低")
    private String priority;

    @Schema(description = "优先级文案")
    private String priorityText;

    @Schema(description = "状态：P 待办 / D 已完成")
    private String status;

    @Schema(description = "状态文案")
    private String statusText;

    @Schema(description = "是否已完成")
    private Boolean done;

    @Schema(description = "是否逾期（计划日期早于今天且仍未完成）")
    private Boolean overdue;

    @Schema(description = "完成时间，未完成为空")
    private LocalDateTime finishTime;

    @Schema(description = "排序号")
    private Integer sortNo;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
