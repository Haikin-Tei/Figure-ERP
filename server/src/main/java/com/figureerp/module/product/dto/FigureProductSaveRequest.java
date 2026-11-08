package com.figureerp.module.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新增 / 编辑手办商品。
 * <p>
 * 关键约束：带盒重量必须为正；商品图片必须 3~4 张（容量要求）。
 */
@Data
@Schema(description = "手办商品保存参数")
public class FigureProductSaveRequest {

    @Schema(description = "商品名称（手办全称）", example = "初音未来 雪未来 1/7 手办", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请填写商品名称")
    private String name;

    @Schema(description = "货号 / SKU，可空，填写后不可与其他商品重复", example = "GSC-12345")
    private String code;

    @Schema(description = "品牌", example = "Good Smile Company")
    private String brand;

    @Schema(description = "系列", example = "Scale")
    private String series;

    @Schema(description = "角色名", example = "初音未来")
    private String characterName;

    @Schema(description = "材质，如 PVC/ABS", example = "PVC/ABS")
    private String material;

    @Schema(description = "长（cm）", example = "28.5")
    private BigDecimal lengthCm;

    @Schema(description = "宽（cm）", example = "18.0")
    private BigDecimal widthCm;

    @Schema(description = "高（cm）", example = "30.0")
    private BigDecimal heightCm;

    @Schema(description = "带盒重量（g），含包装盒", example = "1200", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请填写带盒重量")
    @Positive(message = "带盒重量必须大于 0")
    private Integer weightWithBox;

    @Schema(description = "商品图片 URL 列表，3~4 张", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请至少上传 3 张商品图片")
    @Size(min = 3, max = 4, message = "商品图片需 3~4 张")
    private List<String> images;

    @Schema(description = "状态：1 启用，0 停用", example = "1")
    private Integer status = 1;

    @Schema(description = "备注")
    private String remark;
}
