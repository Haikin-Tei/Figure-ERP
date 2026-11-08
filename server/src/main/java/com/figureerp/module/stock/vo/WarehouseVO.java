package com.figureerp.module.stock.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 仓库出参（列表 / 详情共用）。
 * <p>
 * statusText / isReturnText 由后端翻译好，前端直接展示，避免各页面各写一套映射。
 */
@Data
@Schema(description = "仓库")
public class WarehouseVO {

    private Long id;

    @Schema(description = "仓库代码")
    private String warehouseCode;

    @Schema(description = "仓库名称")
    private String warehouseName;

    @Schema(description = "仓库地址")
    private String warehouseAddress;

    @Schema(description = "是否退货库：N 正常仓 / Y 退货仓")
    private String isReturn;

    @Schema(description = "是否退货库文案：正常仓 / 退货仓")
    private String isReturnText;

    @Schema(description = "状态：A 使用中 / C 盘库中 / D 停用中")
    private String status;

    @Schema(description = "状态文案：使用中 / 盘库中 / 停用中")
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
