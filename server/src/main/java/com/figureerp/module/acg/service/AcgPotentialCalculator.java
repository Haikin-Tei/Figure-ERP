package com.figureerp.module.acg.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 作品「选品潜力分」计算器。
 * <p>
 * 为什么需要它：光看热度或光看销量都会误判——热度高但没人买（叫好不叫座）、
 * 销量高但热度在跌（尾货风险）。所以把三个信号加权成一个 0~100 的分，配合等级用于排序和筛选。
 *
 * <pre>
 * 潜力分 = 热度分 × 45% + 销量分 × 35% + 趋势分 × 20%
 *
 * 热度分 = 当前热度（本身即 0~100）
 * 销量分 = 该作品近 30 天销量 ÷ 全部作品中最高月销量 × 100
 * 趋势分 = 50 + 热度环比涨跌幅(%) × 2，结果夹在 0~100
 *          （无历史快照时按 50 中性处理；涨 25% 及以上封顶 100，跌 25% 及以上见底 0）
 *
 * 等级：S ≥ 75（重点主推）  A ≥ 62（积极备货）  B ≥ 48（常规跟进）  C &lt; 48（谨慎 / 淘汰）
 * </pre>
 * <p>
 * 权重、系数、阈值都是集中在这里的常量，要调整口径只改这一处，
 * 页面上的口径说明也直接引用这里的定义。
 */
public final class AcgPotentialCalculator {

    /** 热度权重 */
    public static final double WEIGHT_HEAT = 0.45;
    /** 销量权重 */
    public static final double WEIGHT_SALES = 0.35;
    /** 趋势权重 */
    public static final double WEIGHT_TREND = 0.20;

    /** 趋势分的中性基准：不涨不跌得 50 分 */
    public static final double TREND_BASE = 50.0;
    /** 热度每变动 1%，趋势分变动 2 分 */
    public static final double TREND_FACTOR = 2.0;

    public static final double LEVEL_S = 75.0;
    public static final double LEVEL_A = 62.0;
    public static final double LEVEL_B = 48.0;

    private AcgPotentialCalculator() {
    }

    /**
     * 计算热度环比涨跌幅（百分比数值，如 10.3 表示上涨 10.3%）。
     *
     * @param current  当前热度
     * @param previous 上一次热度，为空或非正数时视为无历史数据
     */
    public static BigDecimal trendRate(Integer current, Integer previous) {
        if (current == null || previous == null || previous <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP);
    }

    /**
     * 计算潜力分（0~100，保留 1 位小数）。
     *
     * @param heat            当前热度
     * @param monthlySales    近 30 天销量
     * @param maxMonthlySales 全部作品中最高月销量，用于把销量归一到 0~100
     * @param trendRate       热度环比涨跌幅（百分比数值）
     */
    public static BigDecimal score(Integer heat, Integer monthlySales, int maxMonthlySales, BigDecimal trendRate) {
        double h = heat == null ? 0 : clamp(heat, 0, 100);

        double salesNorm = maxMonthlySales > 0
                ? clamp((monthlySales == null ? 0 : monthlySales) * 100.0 / maxMonthlySales, 0, 100)
                : 0;

        double rate = trendRate == null ? 0 : trendRate.doubleValue();
        double trendScore = clamp(TREND_BASE + rate * TREND_FACTOR, 0, 100);

        double total = h * WEIGHT_HEAT + salesNorm * WEIGHT_SALES + trendScore * WEIGHT_TREND;
        return BigDecimal.valueOf(total).setScale(1, RoundingMode.HALF_UP);
    }

    /** 按潜力分给出等级码。 */
    public static String level(BigDecimal score) {
        double v = score == null ? 0 : score.doubleValue();
        if (v >= LEVEL_S) {
            return "S";
        }
        if (v >= LEVEL_A) {
            return "A";
        }
        if (v >= LEVEL_B) {
            return "B";
        }
        return "C";
    }

    /** 等级码 → 中文建议。 */
    public static String levelText(String level) {
        return switch (level == null ? "" : level) {
            case "S" -> "重点主推";
            case "A" -> "积极备货";
            case "B" -> "常规跟进";
            default -> "谨慎 / 淘汰";
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
