package com.figureerp.module.work.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 新增 / 编辑待办的入参（全量提交）。
 * <p>
 * 状态不在入参里：勾选完成走 {@code PUT /api/todo/{id}/status}，
 * 编辑表单只改标题、说明、日期、优先级，避免「编辑时顺手把已完成改回待办」。
 */
@Data
@Schema(description = "待办保存参数")
public class TodoSaveRequest {

    @Schema(description = "待办标题", example = "整理 11 月新品上架清单", requiredMode = Schema.RequiredMode.REQUIRED)
    @Size(max = 120, message = "标题不能超过 120 个字符")
    private String title;

    @Schema(description = "补充说明", example = "按 IP 分类，标出限定款")
    @Size(max = 500, message = "补充说明不能超过 500 个字符")
    private String content;

    @Schema(description = "计划日期，不传默认今天", example = "2026-11-07")
    private LocalDate planDate;

    @Schema(description = "优先级：H 高 / N 普通 / L 低，默认 N", example = "N")
    private String priority;

    @Schema(description = "排序号，越小越靠前，默认 0")
    private Integer sortNo;
}
