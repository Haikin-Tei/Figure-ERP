package com.figureerp.module.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 入库记录列表查询条件。
 * <p>
 * keyword 同时匹配「单号 / 供应商 / 经办人 / 明细里的手办名称」，
 * 其中手办名称需要先查明细再回捞主表，逻辑在 InboundService 里处理。
 */
@Data
@Schema(description = "入库记录查询条件")
public class InboundQuery {

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private long page = 1;

    @Schema(description = "每页条数", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 500, message = "每页条数不能超过 500")
    private long size = 10;

    @Schema(description = "关键字，匹配单号 / 供应商 / 经办人 / 手办名称", example = "RK2026")
    private String keyword;

    @Schema(description = "入库仓库 ID")
    private Long warehouseId;

    @Schema(description = "状态：D 待入库 / C 已入库 / X 已作废")
    private String status;

    @Schema(description = "入库类型：P 采购 / R 退货 / T 调拨 / O 其他")
    private String inboundType;

    @Schema(description = "入库时间起（含当天）", example = "2026-11-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @Schema(description = "入库时间止（含当天）", example = "2026-11-30")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
}
