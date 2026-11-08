package com.figureerp.module.work.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

/**
 * 待办列表查询条件。
 * <p>
 * 归属人固定为当前登录用户，不在参数里出现 —— 避免有人传别人的 id 翻别人的待办。
 */
@Data
@Schema(description = "待办查询条件")
public class TodoQuery {

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private long page = 1;

    @Schema(description = "每页条数", example = "20")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 200, message = "每页条数不能超过 200")
    private long size = 20;

    @Schema(description = "关键字，匹配标题或补充说明")
    private String keyword;

    @Schema(description = "状态：P 待办 / D 已完成，不传表示全部")
    private String status;

    @Schema(description = "优先级：H 高 / N 普通 / L 低，不传表示全部")
    private String priority;

    @Schema(description = "计划日期起（含），格式 yyyy-MM-dd", example = "2026-11-01")
    private LocalDate startDate;

    @Schema(description = "计划日期止（含），格式 yyyy-MM-dd", example = "2026-11-30")
    private LocalDate endDate;
}
