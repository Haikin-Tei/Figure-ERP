package com.figureerp.module.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 货架档案（库存管理 → 仓库设置 → 货架管理 页面维护的主数据）。
 * <p>
 * 一条记录 = 某个仓库下的一个货架。货架编号只在同一仓库内唯一，
 * 例如上海库的 01-1 和杭州库的 01-1 可以同时存在。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shelf")
public class Shelf extends BaseEntity {

    /** 货架编号，如 01-1；同一仓库未删除记录中唯一 */
    private String shelfCode;

    /** 所属仓库 warehouse.id */
    private Long warehouseId;

    /** 库位信息，如 01区-1层 */
    private String location;

    /** 状态：A 正常 / B 维护中 / D 停用 */
    private String status;

    /** 状态描述 / 备注 */
    private String remark;

    /** 排序号，数值越小越靠前 */
    private Integer sortNo;
}
