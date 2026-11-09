package com.figureerp.module.product.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 手办商品档案（主数据）。
 * <p>
 * 记录商品名称、货号、品牌/系列/角色、长宽高、带盒重量，以及 3~4 张商品图片的 URL。
 * {@code images} 在数据库里以逗号分隔的 URL 列表存储，出参时由转换器拆成数组。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("figure_product")
public class FigureProduct extends BaseEntity {

    /** 商品名称（手办全称） */
    private String name;

    /** 货号 / SKU，可空，填写后不可与其他商品重复 */
    private String code;

    /**
     * 厂商 ID（manufacturer.id），商品品牌的来源。
     * <p>
     * {@code ALWAYS} 策略是为了让「编辑时清空厂商」能真正写成 NULL；
     * 因此所有**局部更新**都必须走 LambdaUpdateWrapper，不能拿空实体 updateById。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long manufacturerId;

    /** 品牌名称快照，保存时写入厂商正式名称；老数据可能只有该列（同样需要支持清空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String brand;

    /** 系列，如 Nendoroid / Scale */
    private String series;

    /** 角色名 */
    private String characterName;

    /** 材质，如 PVC/ABS */
    private String material;

    /** 长（cm） */
    private BigDecimal lengthCm;

    /** 宽（cm） */
    private BigDecimal widthCm;

    /** 高（cm） */
    private BigDecimal heightCm;

    /** 带盒重量（g），含包装盒 */
    private Integer weightWithBox;

    /** 商品图片 URL 列表，逗号分隔，3~4 张 */
    private String images;

    /** 备注 */
    private String remark;

    /** 1 启用 / 0 停用 */
    private Integer status;
}
