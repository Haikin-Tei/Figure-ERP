package com.figureerp.module.acg.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * ACG 作品列表查询参数。
 * <p>
 * 名称 / 原名 / 标签为模糊匹配，类型、连载状态、年份、启用状态为精确匹配，
 * 热度支持区间过滤（minHeat ~ maxHeat）。
 */
@Data
public class AcgWorkQuery {

    @Min(value = 1, message = "页码从 1 开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 100, message = "每页最多 100 条")
    private Integer size = 10;

    /** 作品名称 / 原名，模糊 */
    private String name;

    /** 作品编号，模糊 */
    private String workCode;

    /** 类型：ANIME / MANGA / GAME / NOVEL / MOVIE / OTHER */
    private String workType;

    /** 连载状态：ONGOING / FINISHED / UPCOMING */
    private String releaseStatus;

    /** 标签，模糊 */
    private String tag;

    /** 发行年份 */
    private Integer releaseYear;

    /** 热度下限（含） */
    @Min(value = 0, message = "热度不能小于 0")
    @Max(value = 100, message = "热度不能大于 100")
    private Integer minHeat;

    /** 热度上限（含） */
    @Min(value = 0, message = "热度不能小于 0")
    @Max(value = 100, message = "热度不能大于 100")
    private Integer maxHeat;

    /** 状态：1 可选 / 0 停用 */
    private Integer status;

    /**
     * 排序方式，默认按热度从高到低：
     * heatDesc / heatAsc / salesDesc / potentialDesc / yearDesc / createDesc
     */
    private String sortBy = "heatDesc";
}
