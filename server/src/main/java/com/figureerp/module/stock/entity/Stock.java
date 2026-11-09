package com.figureerp.module.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存（库存管理 → 手办列表 页面展示的数据）。
 * <p>
 * 一行 = 某一款手办在某个库位（仓库 / 货架）上的在库数量。
 * 页面按手办汇总展示，展开看库位明细。
 * <p>
 * 本表不提供手工增删改接口：数据只由入库单「确认入库」累加、「作废」回退，
 * 这样库存页和入库记录永远对得上账。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("stock")
public class Stock extends BaseEntity {

    /** 手办商品 figure_product.id */
    private Long productId;

    /** 手办名称快照：商品被下架后，库存页仍要显示这是什么货 */
    private String productName;

    /** 货号快照 */
    private String productCode;

    /** 品牌快照，商品下架后仍能显示品牌 */
    private String brand;

    /** 所在仓库 warehouse.id */
    private Long warehouseId;

    /** 所在货架 shelf.id，可空（入库时没填货架） */
    private Long shelfId;

    /** 在库件数，确认入库累加、作废回退 */
    private Integer quantity;

    /**
     * 库存成本总额 = Σ 入库数量 × 单价。
     * <p>
     * 存总额而不是均价：作废回退时按入库明细的单价直接冲销，账能对上；
     * 存均价则要「反算」，多次不同价入库后除不干净。均价由 总额 / 数量 算出来展示。
     */
    private BigDecimal totalCost;

    /** 最近一次入库时间 */
    private LocalDateTime lastInboundTime;
}
