package com.figureerp.module.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 入库单明细：一行 = 一款手办 + 数量 + 单价。
 * <p>
 * productName / productCode 是下单时的快照，手办之后改名或下架，
 * 历史入库单仍能显示当时的名称，不会因为主数据变更而失真。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inbound_item")
public class InboundItem extends BaseEntity {

    /** 所属入库单 inbound_order.id */
    private Long inboundId;

    /** 手办商品 figure_product.id */
    private Long productId;

    /** 手办名称快照 */
    private String productName;

    /** 货号快照 */
    private String productCode;

    /** 入库数量，必须大于 0 */
    private Integer quantity;

    /** 单价（元） */
    private BigDecimal unitPrice;

    /** 小计金额 = 数量 × 单价，后端计算 */
    private BigDecimal amount;

    /** 备注 */
    private String remark;

    /** 排序号 */
    private Integer sortNo;
}
