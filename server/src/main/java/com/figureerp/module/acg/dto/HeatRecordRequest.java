package com.figureerp.module.acg.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 录入某个作品的当期热度。
 * <p>
 * 一次录入会做两件事：写一条热度快照（同一天重复录入则覆盖），
 * 并把作品的当前热度 / 月销量 / 更新时间同步成最新值。
 */
@Data
public class HeatRecordRequest {

    @NotNull(message = "热度不能为空")
    @Min(value = 0, message = "热度不能小于 0")
    @Max(value = 100, message = "热度不能大于 100")
    private Integer heatScore;

    @Min(value = 0, message = "月销量不能为负数")
    private Integer monthlySales;

    /** 记录日期，不传默认今天 */
    private LocalDate recordDate;

    @Size(max = 255, message = "备注最多 255 个字符")
    private String remark;
}
