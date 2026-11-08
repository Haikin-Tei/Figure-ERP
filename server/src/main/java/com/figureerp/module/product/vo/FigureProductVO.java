package com.figureerp.module.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 手办商品出参（列表 / 详情共用）。
 */
@Data
@Schema(description = "手办商品")
public class FigureProductVO {

    private Long id;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "货号 / SKU")
    private String code;

    @Schema(description = "品牌")
    private String brand;

    @Schema(description = "系列")
    private String series;

    @Schema(description = "角色名")
    private String characterName;

    @Schema(description = "材质")
    private String material;

    @Schema(description = "长（cm）")
    private BigDecimal lengthCm;

    @Schema(description = "宽（cm）")
    private BigDecimal widthCm;

    @Schema(description = "高（cm）")
    private BigDecimal heightCm;

    @Schema(description = "带盒重量（g），含包装盒")
    private Integer weightWithBox;

    @Schema(description = "商品图片 URL 列表，3~4 张")
    private List<String> images;

    @Schema(description = "规格文案，如「28.5×18.0×30.0 cm / 带盒 1200 g」")
    private String specLabel;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "状态：1 启用，0 停用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
