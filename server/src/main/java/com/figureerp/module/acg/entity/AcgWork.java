package com.figureerp.module.acg.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.figureerp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * ACG 作品档案（主数据）。
 * <p>
 * 一部作品 = 一条记录，维护名称、类型、版权方/制作公司、连载状态，
 * 以及当前热度与销量指标；历史热度另存 {@link AcgHeatLog}，用于趋势分析。
 * <p>
 * {@code tags} 在数据库里以逗号分隔的字符串存储，出参时由转换器拆成数组。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("acg_work")
public class AcgWork extends BaseEntity {

    /** 作品编号，如 ACG-001，可空，填写后不可与其他作品重复 */
    private String workCode;

    /** 作品名称（中文） */
    private String name;

    /** 原名（日文 / 英文） */
    private String originalName;

    /** 类型：ANIME / MANGA / GAME / NOVEL / MOVIE / OTHER */
    private String workType;

    /** 版权方 / 发行方 */
    private String ipOwner;

    /** 制作公司 / 开发商 */
    private String studio;

    /** 作者 / 原作 */
    private String author;

    /** 首播 / 发行年份 */
    private Integer releaseYear;

    /** 连载状态：ONGOING 连载中 / FINISHED 已完结 / UPCOMING 待播出 */
    private String releaseStatus;

    /** 当前热度指数 0-100 */
    private Integer heatScore;

    /** 近 30 天周边销量（件） */
    private Integer monthlySales;

    /** 累计销量（件） */
    private Integer totalSales;

    /** 标签，逗号分隔 */
    private String tags;

    /** 封面图 URL */
    private String coverImage;

    /** 热度最近更新时间 */
    private LocalDateTime heatUpdatedAt;

    /** 备注 */
    private String remark;

    /** 1 可选（出现在其他表的下拉选项）/ 0 停用 */
    private Integer status;
}
