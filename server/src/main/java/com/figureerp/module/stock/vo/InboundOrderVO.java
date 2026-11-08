package com.figureerp.module.stock.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 入库单出参（列表 / 详情共用）。
 * <p>
 * 列表场景只填主表 + 汇总字段（itemCount / productSummary），{@code items} 为空；
 * 详情接口才会带完整明细，避免列表接口一次拉回几百行明细。
 * 仓库、区域、货架的名称由后端按 id 关联查出，前端不用再查一遍。
 */
@Data
@Schema(description = "入库单")
public class InboundOrderVO {

    private Long id;

    @Schema(description = "入库单号，如 RK202611070001")
    private String inboundNo;

    @Schema(description = "入库类型：P 采购 / R 退货 / T 调拨 / O 其他")
    private String inboundType;

    @Schema(description = "入库类型文案：采购入库 / 退货入库 / 调拨入库 / 其他入库")
    private String inboundTypeText;

    @Schema(description = "入库仓库 ID")
    private Long warehouseId;

    @Schema(description = "入库仓库代码")
    private String warehouseCode;

    @Schema(description = "入库仓库名称")
    private String warehouseName;

    @Schema(description = "入库区域 ID")
    private Long areaId;

    @Schema(description = "入库区域名称")
    private String areaName;

    @Schema(description = "入库货架 ID")
    private Long shelfId;

    @Schema(description = "入库货架编号")
    private String shelfCode;

    @Schema(description = "供应商 / 来源单位")
    private String supplier;

    @Schema(description = "状态：D 待入库 / C 已入库 / X 已作废")
    private String status;

    @Schema(description = "状态文案")
    private String statusText;

    @Schema(description = "明细数量合计")
    private Integer totalQuantity;

    @Schema(description = "明细金额合计")
    private BigDecimal totalAmount;

    @Schema(description = "明细行数（几款手办）")
    private Integer itemCount;

    @Schema(description = "手办摘要，如「初音未来 雪未来 等 3 款」")
    private String productSummary;

    @Schema(description = "入库时间")
    private LocalDateTime inboundTime;

    @Schema(description = "经办人")
    private String operator;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "手办明细，仅详情接口返回")
    private List<InboundItemVO> items;
}
