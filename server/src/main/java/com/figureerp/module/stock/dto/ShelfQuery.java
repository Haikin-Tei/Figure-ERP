package com.figureerp.module.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 货架列表查询条件（分页 + 关键字模糊 + 仓库/状态精确过滤）。
 */
@Data
@Schema(description = "货架查询条件")
public class ShelfQuery {

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private long page = 1;

    @Schema(description = "每页条数", example = "20")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 500, message = "每页条数不能超过 500")
    private long size = 20;

    @Schema(description = "关键字，匹配货架编号或库位信息", example = "01-1")
    private String keyword;

    @Schema(description = "所属仓库 ID", example = "1")
    private Long warehouseId;

    @Schema(description = "状态：A 正常 / B 维护中 / D 停用")
    private String status;
}
