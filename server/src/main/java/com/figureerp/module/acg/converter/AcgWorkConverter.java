package com.figureerp.module.acg.converter;

import com.figureerp.module.acg.entity.AcgWork;
import com.figureerp.module.acg.service.AcgPotentialCalculator;
import com.figureerp.module.acg.vo.AcgWorkVO;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * ACG 作品实体 → 出参。
 * <p>
 * 转换时顺带算好「趋势」和「潜力分」，这两个值依赖外部上下文：
 * 全库最高月销量（用于销量归一化）和上一次热度（用于环比），由调用方传入。
 */
public final class AcgWorkConverter {

    private AcgWorkConverter() {
    }

    /**
     * @param w               作品实体
     * @param maxMonthlySales 全库最高月销量，用于销量归一化；0 表示没有销量数据
     * @param prevHeat        上一次录入的热度，null 表示没有历史快照
     */
    public static AcgWorkVO toVO(AcgWork w, int maxMonthlySales, Integer prevHeat) {
        AcgWorkVO vo = new AcgWorkVO();
        vo.setId(w.getId());
        vo.setWorkCode(w.getWorkCode());
        vo.setName(w.getName());
        vo.setOriginalName(w.getOriginalName());
        vo.setWorkType(w.getWorkType());
        vo.setWorkTypeText(AcgEnums.typeText(w.getWorkType()));
        vo.setIpOwner(w.getIpOwner());
        vo.setStudio(w.getStudio());
        vo.setAuthor(w.getAuthor());
        vo.setReleaseYear(w.getReleaseYear());
        vo.setReleaseStatus(w.getReleaseStatus());
        vo.setReleaseStatusText(AcgEnums.releaseStatusText(w.getReleaseStatus()));
        vo.setHeatScore(w.getHeatScore());
        vo.setMonthlySales(w.getMonthlySales());
        vo.setTotalSales(w.getTotalSales());
        vo.setTags(splitTags(w.getTags()));
        vo.setCoverImage(w.getCoverImage());
        vo.setHeatUpdatedAt(w.getHeatUpdatedAt());
        vo.setRemark(w.getRemark());
        vo.setStatus(w.getStatus());

        BigDecimal trendRate = AcgPotentialCalculator.trendRate(w.getHeatScore(), prevHeat);
        vo.setTrendRate(trendRate);
        vo.setTrendText(trendText(trendRate));

        BigDecimal score = AcgPotentialCalculator.score(w.getHeatScore(), w.getMonthlySales(), maxMonthlySales, trendRate);
        String level = AcgPotentialCalculator.level(score);
        vo.setPotentialScore(score);
        vo.setPotentialLevel(level);
        vo.setPotentialLevelText(AcgPotentialCalculator.levelText(level));

        vo.setCreateTime(w.getCreateTime());
        vo.setUpdateTime(w.getUpdateTime());
        return vo;
    }

    /** 涨跌幅小于 0.5% 视为持平，避免 0.1% 的噪声也显示成「上升」 */
    public static String trendText(BigDecimal trendRate) {
        double v = trendRate == null ? 0 : trendRate.doubleValue();
        if (v >= 0.5) {
            return "上升";
        }
        if (v <= -0.5) {
            return "下降";
        }
        return "持平";
    }

    /** 逗号分隔字符串 → 标签列表 */
    public static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    /** 标签列表 → 逗号分隔字符串，空列表返回 null，避免存成空串 */
    public static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String joined = tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return joined.isEmpty() ? null : joined;
    }
}
