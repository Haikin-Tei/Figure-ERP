package com.figureerp.module.overtime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 加班单列表查询条件。
 * <p>
 * 日期区间按「加班开始时间」过滤；执行级账号（人事岗除外）看列表时后端强制只看自己。
 */
@Data
@Schema(description = "加班单查询条件")
public class OvertimeQuery {

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private long page = 1;

    @Schema(description = "每页条数", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 500, message = "每页条数不能超过 500")
    private long size = 10;

    @Schema(description = "关键字，匹配单号 / 申请人姓名 / 加班事由", example = "张三")
    private String keyword;

    @Schema(description = "申请人 ID，主管按人筛选时使用")
    private Long userId;

    @Schema(description = "加班类型：W 工作日 / O 休息日 / H 节假日")
    private String overtimeType;

    @Schema(description = "状态：P 待审批 / A 已通过 / R 已驳回 / C 已撤销")
    private String status;

    @Schema(description = "加班开始日期起（含当天）", example = "2026-11-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @Schema(description = "加班开始日期止（含当天）", example = "2026-11-30")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
}
