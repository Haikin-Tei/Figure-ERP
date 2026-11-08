package com.figureerp.module.acg.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * ACG 作品热度快照。
 * <p>
 * 每次在页面上录入热度写一条，用于画趋势曲线、算涨跌幅。
 * 同一天重复录入同作品时由 Service 覆盖当天记录，不产生重复点。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("acg_heat_log")
public class AcgHeatLog extends BaseEntity {

    /** 作品 ID */
    private Long workId;

    /** 记录日期 */
    private LocalDate recordDate;

    /** 当日热度指数 0-100 */
    private Integer heatScore;

    /** 当日近 30 天销量（件） */
    private Integer monthlySales;

    /** 备注 */
    private String remark;
}
