package com.figureerp.module.acg.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ACG 作品出参。
 * <p>
 * 除档案字段外，还带上三个「算出来的」字段，前端不用自己算：
 * <ul>
 *   <li>{@code trendRate} 热度环比涨跌幅（%），有历史快照才有值，否则 0；</li>
 *   <li>{@code potentialScore} / {@code potentialLevel} 选品潜力分与等级，口径见 AcgPotentialCalculator；</li>
 *   <li>{@code workTypeText} 等文案字段，避免前端各处各译一遍。</li>
 * </ul>
 */
@Data
public class AcgWorkVO {

    private Long id;

    private String workCode;

    private String name;

    private String originalName;

    private String workType;

    private String workTypeText;

    private String ipOwner;

    private String studio;

    private String author;

    private Integer releaseYear;

    private String releaseStatus;

    private String releaseStatusText;

    /** 当前热度 0-100 */
    private Integer heatScore;

    /** 近 30 天销量 */
    private Integer monthlySales;

    /** 累计销量 */
    private Integer totalSales;

    private List<String> tags;

    private String coverImage;

    private LocalDateTime heatUpdatedAt;

    private String remark;

    private Integer status;

    /** 热度环比涨跌幅（%），如 10.3 表示上涨 10.3% */
    private BigDecimal trendRate;

    /** 趋势文案：上升 / 下降 / 持平 */
    private String trendText;

    /** 选品潜力分 0-100 */
    private BigDecimal potentialScore;

    /** 潜力等级 S/A/B/C */
    private String potentialLevel;

    /** 潜力等级中文建议 */
    private String potentialLevelText;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
