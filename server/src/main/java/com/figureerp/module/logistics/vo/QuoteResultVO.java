package com.figureerp.module.logistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 报价对比结果：给定出发地、目的地与重量，列出所有可用的供应商报价。
 */
@Data
@Schema(description = "运费报价对比结果")
public class QuoteResultVO {

    @Schema(description = "出发地展示文案")
    private String originLabel;

    @Schema(description = "目的地展示文案")
    private String destLabel;

    @Schema(description = "所属运费区域")
    private String zoneLabel;

    @Schema(description = "查询重量（kg）")
    private BigDecimal weight;

    @Schema(description = "各家报价，按总价从低到高")
    private List<QuoteItemVO> items;

    @Schema(description = "报价数量")
    private int count;

    @Schema(description = "最低价（元）")
    private BigDecimal lowestPrice;

    @Schema(description = "最低价供应商")
    private String lowestSupplier;

    @Schema(description = "最短时效（天）")
    private Integer fastestDays;

    @Schema(description = "拼单建议：价格最低与时效最快的说明")
    private String advice;
}
