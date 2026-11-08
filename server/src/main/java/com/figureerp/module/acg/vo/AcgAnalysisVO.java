package com.figureerp.module.acg.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * ACG 作品分析总览。
 * <p>
 * 一次返回页面「热度与选品分析」需要的全部数据：KPI、排行、四象限散点、类型分布，
 * 以及由后端算好的文字结论（insights），避免前端自己拼判断逻辑。
 */
public record AcgAnalysisVO(
        Kpi kpi,
        List<RankItem> ranking,
        List<QuadrantPoint> quadrant,
        List<TypeShare> typeShares,
        List<String> insights,
        /** 潜力分口径说明，页面直接展示，保证「怎么算的」对使用者透明 */
        String formula
) {

    /** 顶部关键指标 */
    public record Kpi(
            int workCount,
            int selectableCount,
            BigDecimal avgHeat,
            int monthlySalesTotal,
            BigDecimal avgPotential,
            int levelSCount,
            int levelACount,
            int levelBCount,
            int levelCCount,
            String topTypeText,
            int topTypeCount
    ) {
    }

    /** 排行条目（前端可按热度 / 销量 / 潜力分自行重排） */
    public record RankItem(
            Long id,
            String name,
            String coverImage,
            String workType,
            String workTypeText,
            Integer heatScore,
            Integer monthlySales,
            BigDecimal potentialScore,
            String potentialLevel,
            BigDecimal trendRate
    ) {
    }

    /** 四象限散点 */
    public record QuadrantPoint(
            Long id,
            String name,
            Integer heatScore,
            Integer monthlySales,
            String potentialLevel,
            String quadrant,
            String quadrantText
    ) {
    }

    /** 类型分布 */
    public record TypeShare(
            String workType,
            String workTypeText,
            int count,
            int monthlySales,
            BigDecimal avgHeat
    ) {
    }
}
