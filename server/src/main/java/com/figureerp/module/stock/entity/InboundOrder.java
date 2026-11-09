package com.figureerp.module.stock.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入库单（库存管理 → 入库记录 页面维护的数据）。
 * <p>
 * 一条记录 = 一次入库业务，手办明细在 {@link InboundItem}（inbound_item 表）。
 * 主表上的 totalQuantity / totalAmount 是明细的合计，由服务层按明细重算后写入，
 * 不接受前端传值，避免列表上的数字和明细对不上。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inbound_order")
public class InboundOrder extends BaseEntity {

    /** 入库单号，如 RK202611070001；未删除记录中唯一，留空时后端自动生成 */
    private String inboundNo;

    /** 入库类型：P 采购 / R 退货 / T 调拨 / O 其他，见 InboundType */
    private String inboundType;

    /** 入库仓库 warehouse.id */
    private Long warehouseId;

    // 下面几个字段可空，编辑时用户可能把它们清空，
    // MyBatis-Plus 默认忽略 null（NOT_NULL 策略），这里改成 ALWAYS，null 才会真正写回库里
    /** 入库货架 shelf.id，可空；填写时须与 warehouseId 同仓库 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long shelfId;

    /** 供应商 / 来源单位 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String supplier;

    /** 状态：D 待入库 / C 已入库 / X 已作废，见 InboundStatus */
    private String status;

    /** 明细数量合计（后端重算） */
    private Integer totalQuantity;

    /** 明细金额合计（后端重算） */
    private BigDecimal totalAmount;

    /** 入库时间 */
    private LocalDateTime inboundTime;

    /** 经办人姓名，新增时默认取当前登录用户 */
    private String operator;

    /** 备注 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
