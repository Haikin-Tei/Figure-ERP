package com.figureerp.module.logistics.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 物流价格（首重 + 续重）。
 * <p>
 * 一条记录描述「某供应商 从某出发地 到某目的地」的一档价格。
 * {@code destCity} 为空表示该省全省统一价；填了城市则只对该城市生效。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("logistics_price")
public class LogisticsPrice extends BaseEntity {

    /** 物流供应商编码，见 LogisticsSupplier 枚举 */
    private String supplierCode;

    /** 出发地-省 */
    private String originProvince;

    /** 出发地-市 */
    private String originCity;

    /** 出发地-区/县 */
    private String originDistrict;

    /** 目的地-省 */
    private String destProvince;

    /** 目的地-市，为空表示全省统一价 */
    private String destCity;

    /** 首重重量（kg） */
    private BigDecimal firstWeight;

    /** 首重价格（元） */
    private BigDecimal firstPrice;

    /** 续重单位（kg） */
    private BigDecimal additionalWeight;

    /** 续重单价（元） */
    private BigDecimal additionalPrice;

    /** 预计时效（天） */
    private Integer estimatedDays;

    /** 1 启用 / 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
