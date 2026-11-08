package com.figureerp.module.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增 / 编辑仓库。
 * <p>
 * 状态与类型用单字符与前端下拉保持一致：status ∈ {A,C,D}，isReturn ∈ {N,Y}。
 */
@Data
@Schema(description = "仓库保存参数")
public class WarehouseSaveRequest {

    @Schema(description = "仓库代码，如 3101，未删除记录中唯一", example = "3101", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写仓库代码")
    @Size(max = 50, message = "仓库代码不能超过 50 个字符")
    private String warehouseCode;

    @Schema(description = "仓库名称", example = "上海库", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写仓库名称")
    @Size(max = 100, message = "仓库名称不能超过 100 个字符")
    private String warehouseName;

    @Schema(description = "仓库地址", example = "上海市浦东新区新川路180号")
    @Size(max = 255, message = "仓库地址不能超过 255 个字符")
    private String warehouseAddress;

    @Schema(description = "是否退货库：N 正常仓 / Y 退货仓，默认 N")
    @Pattern(regexp = "^[NY]$", message = "是否退货库只能是 N 或 Y")
    private String isReturn;

    @Schema(description = "状态：A 使用中 / C 盘库中 / D 停用中，默认 A")
    @Pattern(regexp = "^[ACD]$", message = "仓库状态只能是 A、C 或 D")
    private String status;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

    @Schema(description = "排序号，数值越小越靠前")
    private Integer sortNo;
}
