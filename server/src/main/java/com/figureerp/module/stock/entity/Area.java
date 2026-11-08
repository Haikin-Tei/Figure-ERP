package com.figureerp.module.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 区域档案（库存管理 → 仓库设置 → 区域配置 页面维护的主数据）。
 * <p>
 * 一条记录 = 某个仓库下的一片区域（如 A区-核心展示柜），
 * 是「仓库 → 区域 → 货架 → 库位」层级里的中间一层。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("area")
public class Area extends BaseEntity {

    /** 区域编号，如 A；同一仓库未删除记录中唯一 */
    private String areaCode;

    /** 区域名称，如 A区-核心展示柜 */
    private String areaName;

    /** 所属仓库 warehouse.id */
    private Long warehouseId;

    /** 物理位置描述，如 一层靠窗 */
    private String location;

    /** 可容纳货架数（原页面「容量 / 格子数」） */
    private Integer capacity;

    /** 状态：A 使用中 / D 停用 */
    private String status;

    /** 备注 */
    private String remark;

    /** 排序号，数值越小越靠前 */
    private Integer sortNo;
}
