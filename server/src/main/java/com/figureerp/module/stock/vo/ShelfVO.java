package com.figureerp.module.stock.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 货架出参（列表 / 详情共用）。
 * <p>
 * warehouseCode / warehouseName 由后端按 warehouse_id 关联查出来一起返回，
 * 前端列表直接展示，不用再为每一行单独请求仓库接口。
 */
@Data
@Schema(description = "货架")
public class ShelfVO {

    private Long id;

    @Schema(description = "货架编号")
    private String shelfCode;

    @Schema(description = "所属仓库 ID")
    private Long warehouseId;

    @Schema(description = "所属仓库代码")
    private String warehouseCode;

    @Schema(description = "所属仓库名称")
    private String warehouseName;

    @Schema(description = "所属区域 ID，可空")
    private Long areaId;

    @Schema(description = "所属区域名称，可空")
    private String areaName;

    @Schema(description = "库位信息，如 01区-1层")
    private String location;

    @Schema(description = "状态：A 正常 / B 维护中 / D 停用")
    private String status;

    @Schema(description = "状态文案：正常 / 维护中 / 停用")
    private String statusText;

    @Schema(description = "状态描述 / 备注")
    private String remark;

    @Schema(description = "排序号")
    private Integer sortNo;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
