package com.figureerp.module.acg.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.figureerp.module.acg.converter.AcgEnums;
import com.figureerp.module.acg.converter.AcgWorkConverter;
import com.figureerp.module.acg.entity.AcgWork;
import com.figureerp.module.acg.mapper.AcgWorkMapper;
import com.figureerp.module.acg.vo.AcgAnalysisVO;
import com.figureerp.module.acg.vo.AcgWorkVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 作品热度与选品分析。
 * <p>
 * 这里回答三个问题：
 * <ol>
 *   <li>现在哪些作品最热、卖得最好（KPI + 排行）；</li>
 *   <li>热度和销量是否匹配（四象限），哪些是「叫好不叫座」、哪些是「闷声卖货」；</li>
 *   <li>哪个类型整体更能带货（类型分布），并给出可直接执行的文字建议。</li>
 * </ol>
 * 所有指标都由库存作品实时算出，不额外落统计表，避免数据不一致。
 */
@Service
@RequiredArgsConstructor
public class AcgAnalysisService {

    /** 潜力分口径说明，随分析结果一起返回，页面直接展示 */
    private static final String FORMULA =
            "潜力分 = 热度 × 45% + 销量分 × 35% + 趋势分 × 20%；"
                    + "销量分 = 本作品近 30 天销量 ÷ 库内最高月销量 × 100；"
                    + "趋势分 = 50 + 热度环比涨跌幅(%) × 2，范围 0~100。"
                    + "等级：S ≥ 75 重点主推，A ≥ 62 积极备货，B ≥ 48 常规跟进，C < 48 谨慎 / 淘汰。";

    private final AcgWorkMapper workMapper;
    private final AcgMetricsService metricsService;

    public AcgAnalysisVO analysis() {
        List<AcgWork> works = workMapper.selectList(Wrappers.<AcgWork>lambdaQuery());
        if (works.isEmpty()) {
            return new AcgAnalysisVO(
                    new AcgAnalysisVO.Kpi(0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, 0, 0, 0, "—", 0),
                    List.of(), List.of(), List.of(),
                    List.of("还没有作品数据，先在「作品目录」里录入几部作品（记得填热度），分析就会出现在这里。"),
                    FORMULA);
        }

        int maxSales = works.stream().mapToInt(w -> nz(w.getMonthlySales())).max().orElse(0);
        Map<Long, Integer> prevHeat = metricsService.prevHeat(works.stream().map(AcgWork::getId).toList());

        List<AcgWorkVO> vos = works.stream()
                .map(w -> AcgWorkConverter.toVO(w, maxSales, prevHeat.get(w.getId())))
                .sorted(Comparator.comparing(AcgWorkVO::getPotentialScore).reversed())
                .toList();

        return new AcgAnalysisVO(
                buildKpi(vos),
                buildRanking(vos),
                buildQuadrant(vos),
                buildTypeShares(vos),
                buildInsights(vos),
                FORMULA);
    }

    /* ---------- KPI ---------- */

    private AcgAnalysisVO.Kpi buildKpi(List<AcgWorkVO> vos) {
        int count = vos.size();
        int selectable = (int) vos.stream().filter(v -> Integer.valueOf(1).equals(v.getStatus())).count();
        int salesTotal = vos.stream().mapToInt(v -> nz(v.getMonthlySales())).sum();
        BigDecimal avgHeat = avg(vos.stream().mapToInt(v -> nz(v.getHeatScore())).sum(), count);
        BigDecimal avgPotential = avgBig(vos.stream().map(AcgWorkVO::getPotentialScore)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add), count);

        int s = 0, a = 0, b = 0, c = 0;
        for (AcgWorkVO v : vos) {
            switch (v.getPotentialLevel() == null ? "C" : v.getPotentialLevel()) {
                case "S" -> s++;
                case "A" -> a++;
                case "B" -> b++;
                default -> c++;
            }
        }

        // 作品数最多的类型，作为「当前主力赛道」
        Map<String, Long> byType = vos.stream()
                .collect(Collectors.groupingBy(AcgWorkVO::getWorkType, LinkedHashMap::new, Collectors.counting()));
        String topType = byType.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");

        return new AcgAnalysisVO.Kpi(count, selectable, avgHeat, salesTotal, avgPotential,
                s, a, b, c, AcgEnums.typeText(topType), byType.getOrDefault(topType, 0L).intValue());
    }

    /* ---------- 排行 ---------- */

    private List<AcgAnalysisVO.RankItem> buildRanking(List<AcgWorkVO> vos) {
        return vos.stream()
                .map(v -> new AcgAnalysisVO.RankItem(
                        v.getId(), v.getName(), v.getCoverImage(), v.getWorkType(), v.getWorkTypeText(),
                        v.getHeatScore(), v.getMonthlySales(), v.getPotentialScore(),
                        v.getPotentialLevel(), v.getTrendRate()))
                .toList();
    }

    /* ---------- 四象限 ---------- */

    /**
     * 以「平均热度 / 平均月销量」为十字分割线，把作品分到四个象限：
     * 双高=主推，高热度低销量=待转化（叫好不叫座），低热度高销量=闷声卖货（可加推），
     * 双低=谨慎。平均线比固定阈值更贴合当前库内实际水平。
     */
    private List<AcgAnalysisVO.QuadrantPoint> buildQuadrant(List<AcgWorkVO> vos) {
        int count = vos.size();
        double avgHeat = vos.stream().mapToInt(v -> nz(v.getHeatScore())).average().orElse(0);
        double avgSales = vos.stream().mapToInt(v -> nz(v.getMonthlySales())).average().orElse(0);

        return vos.stream().map(v -> {
            boolean highHeat = nz(v.getHeatScore()) >= avgHeat;
            boolean highSales = nz(v.getMonthlySales()) >= avgSales;
            String quadrant;
            String text;
            if (highHeat && highSales) {
                quadrant = "STAR";
                text = "双高 · 主推";
            } else if (highHeat) {
                quadrant = "HEAT_ONLY";
                text = "高热度 · 待转化";
            } else if (highSales) {
                quadrant = "SALES_ONLY";
                text = "低热度 · 闷声卖货";
            } else {
                quadrant = "LOW";
                text = "双低 · 谨慎";
            }
            return new AcgAnalysisVO.QuadrantPoint(v.getId(), v.getName(), v.getHeatScore(),
                    v.getMonthlySales(), v.getPotentialLevel(), quadrant, text);
        }).toList();
    }

    /* ---------- 类型分布 ---------- */

    private List<AcgAnalysisVO.TypeShare> buildTypeShares(List<AcgWorkVO> vos) {
        Map<String, List<AcgWorkVO>> grouped = vos.stream()
                .collect(Collectors.groupingBy(AcgWorkVO::getWorkType, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(e -> {
                    List<AcgWorkVO> list = e.getValue();
                    int sales = list.stream().mapToInt(v -> nz(v.getMonthlySales())).sum();
                    BigDecimal avgHeat = avg(list.stream().mapToInt(v -> nz(v.getHeatScore())).sum(), list.size());
                    return new AcgAnalysisVO.TypeShare(e.getKey(), AcgEnums.typeText(e.getKey()),
                            list.size(), sales, avgHeat);
                })
                .sorted(Comparator.comparingInt(AcgAnalysisVO.TypeShare::monthlySales).reversed())
                .toList();
    }

    /* ---------- 文字结论 ---------- */

    private List<String> buildInsights(List<AcgWorkVO> vos) {
        List<String> tips = new ArrayList<>();

        vos.stream().max(Comparator.comparingInt(v -> nz(v.getMonthlySales())))
                .ifPresent(v -> tips.add(String.format(
                        "当前带货王：《%s》近 30 天卖出 %s 件，热度 %d，属于「%s」档，优先保证它的备货与排期。",
                        v.getName(), formatNumber(nz(v.getMonthlySales())), nz(v.getHeatScore()), v.getPotentialLevelText())));

        vos.stream()
                .filter(v -> v.getTrendRate() != null && v.getTrendRate().doubleValue() > 0)
                .max(Comparator.comparing(AcgWorkVO::getTrendRate))
                .ifPresent(v -> tips.add(String.format(
                        "涨得最快：《%s》热度环比 %s%%，是近期话题度上升最明显的作品，适合提前锁定下一批货。",
                        v.getName(), v.getTrendRate().toPlainString())));

        vos.stream()
                .filter(v -> v.getTrendRate() != null && v.getTrendRate().doubleValue() < 0)
                .min(Comparator.comparing(AcgWorkVO::getTrendRate))
                .ifPresent(v -> tips.add(String.format(
                        "需要警惕：《%s》热度环比 %s%%，月销 %s 件，建议控制补货深度，避免压货。",
                        v.getName(), v.getTrendRate().toPlainString(), formatNumber(nz(v.getMonthlySales())))));

        List<AcgWorkVO> top = vos.stream().filter(v -> "S".equals(v.getPotentialLevel())).limit(3).toList();
        if (!top.isEmpty()) {
            tips.add("S 级重点主推：" + top.stream()
                    .map(v -> "《" + v.getName() + "》(" + v.getPotentialScore().toPlainString() + " 分)")
                    .collect(Collectors.joining("、"))
                    + "，热度与销量双高且趋势向上。");
        }

        Map<String, Integer> salesByType = vos.stream()
                .collect(Collectors.groupingBy(AcgWorkVO::getWorkType,
                        Collectors.summingInt(v -> nz(v.getMonthlySales()))));
        salesByType.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(e -> tips.add(String.format(
                        "类型机会：「%s」类作品本月合计卖出 %s 件，是当前最能带货的品类，选品时可以适度倾斜。",
                        AcgEnums.typeText(e.getKey()), formatNumber(e.getValue()))));

        return tips;
    }

    /* ---------- 小工具 ---------- */

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal avg(int total, int count) {
        if (count <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal avgBig(BigDecimal total, int count) {
        if (count <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return total.divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }

    private static String formatNumber(int value) {
        return String.format("%,d", value);
    }
}
