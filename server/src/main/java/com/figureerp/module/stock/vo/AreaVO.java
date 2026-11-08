package com.figureerp.module.stock.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 区域出参（列表 / 详情共用）。
 * <p>
 * warehouseCode / warehouseName 由后端按 warehouse_id 关联查出，
 * shelfCount 是该区域下未删除的货架数，页面直接展示「已放货架 3/20」这类信息，不用前端再查一遍。
 */
@Data
@Schema(description = "区域")
public class AreaVO {

    private Long id;

    @Schema(description = "区域编号，如 A")
    private String areaCode;

    @Schema(description = "区域名称，如 A区-核心展示柜")
    private String areaName;

    @Schema(description = "所属仓库 ID")
    private Long warehouseId;

    @Schema(description = "所属仓库代码")
    private String warehouseCode;

    @Schema(description = "所属仓库名称")
    private String warehouseName;

    @Schema(description = "物理位置，如 一层靠窗")
    private String location;

    @Schema(description = "可容纳货架数")
    private Integer capacity;

    @Schema(description = "该区域下未删除的货架数")
    private Long shelfCount;

    @Schema(description = "状态：A 使用中 / D 停用")
    private String status;

    @Schema(description = "状态文案：使用中 / 停用")
    private String statusText;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "排序号")
    private Integer sortNo;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
