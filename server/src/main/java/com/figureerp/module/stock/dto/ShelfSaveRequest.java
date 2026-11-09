package com.figureerp.module.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增 / 编辑货架。
 * <p>
 * 货架编号的唯一范围是「同一仓库 + 未删除」，所以 warehouseId 必填；
 * status ∈ {A,B,D}，不传按 A 处理。
 */
@Data
@Schema(description = "货架保存参数")
public class ShelfSaveRequest {

    @Schema(description = "货架编号，如 01-1，同一仓库未删除记录中唯一", example = "01-1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写货架编号")
    @Size(max = 50, message = "货架编号不能超过 50 个字符")
    private String shelfCode;

    @Schema(description = "所属仓库 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择所属仓库")
    private Long warehouseId;

    @Schema(description = "库位信息，如 01区-1层", example = "01区-1层")
    @Size(max = 100, message = "库位信息不能超过 100 个字符")
    private String location;

    @Schema(description = "状态：A 正常 / B 维护中 / D 停用，默认 A")
    @Pattern(regexp = "^[ABD]$", message = "货架状态只能是 A、B 或 D")
    private String status;

    @Schema(description = "状态描述 / 备注")
    @Size(max = 255, message = "状态描述不能超过 255 个字符")
    private String remark;

    @Schema(description = "排序号，数值越小越靠前")
    private Integer sortNo;
}
