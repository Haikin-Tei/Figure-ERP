package com.figureerp.module.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增 / 编辑区域。
 * <p>
 * 区域编号的唯一范围是「同一仓库 + 未删除」，所以 warehouseId 必填；
 * status ∈ {A,D}，不传按 A 处理；capacity 是容量（可容纳货架数），不传按 0。
 */
@Data
@Schema(description = "区域保存参数")
public class AreaSaveRequest {

    @Schema(description = "区域编号，如 A，同一仓库未删除记录中唯一", example = "A",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写区域编号")
    @Size(max = 50, message = "区域编号不能超过 50 个字符")
    private String areaCode;

    @Schema(description = "区域名称，如 A区-核心展示柜", example = "A区-核心展示柜",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写区域名称")
    @Size(max = 100, message = "区域名称不能超过 100 个字符")
    private String areaName;

    @Schema(description = "所属仓库 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择所属仓库")
    private Long warehouseId;

    @Schema(description = "物理位置，如 一层靠窗", example = "一层靠窗")
    @Size(max = 100, message = "物理位置不能超过 100 个字符")
    private String location;

    @Schema(description = "容量：可容纳货架数，默认 0", example = "20")
    @Min(value = 0, message = "容量不能小于 0")
    @Max(value = 9999, message = "容量不能超过 9999")
    private Integer capacity;

    @Schema(description = "状态：A 使用中 / D 停用，默认 A")
    @Pattern(regexp = "^[AD]$", message = "区域状态只能是 A 或 D")
    private String status;

    @Schema(description = "备注")
    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;

    @Schema(description = "排序号，数值越小越靠前")
    private Integer sortNo;
}
