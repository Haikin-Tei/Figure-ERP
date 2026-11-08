package com.figureerp.module.stock.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 仓库档案（库存管理 → 仓库设置 → 库位管理 页面维护的主数据）。
 * <p>
 * 一条记录 = 一个仓库，区分正常仓 / 退货仓，状态为「使用中 / 盘库中 / 停用中」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("warehouse")
public class Warehouse extends BaseEntity {

    /** 仓库代码，如 3101；未删除记录中唯一 */
    private String warehouseCode;

    /** 仓库名称，如 上海库 */
    private String warehouseName;

    /** 仓库地址 */
    private String warehouseAddress;

    /** 是否退货库：N 正常仓 / Y 退货仓 */
    private String isReturn;

    /** 状态：A 使用中 / C 盘库中 / D 停用中 */
    private String status;

    /** 备注 */
    private String remark;

    /** 排序号，数值越小越靠前 */
    private Integer sortNo;
}
