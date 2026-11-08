package com.figureerp.module.acg.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.figureerp.module.acg.entity.AcgHeatLog;
import com.figureerp.module.acg.entity.AcgWork;
import com.figureerp.module.acg.mapper.AcgHeatLogMapper;
import com.figureerp.module.acg.mapper.AcgWorkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 列表 / 分析共用的指标查询。
 * <p>
 * 两个口径只在内存里算，不落库：
 * <ul>
 *   <li>全库最高月销量：把销量归一到 0~100，避免不同量级的作品直接比较；</li>
 *   <li>每个作品的上一次热度：取该作品最近两条快照的第二条，用于算环比。</li>
 * </ul>
 * 这样只要录入新热度，趋势和潜力分就会自动变化，不需要额外维护冗余字段。
 */
@Service
@RequiredArgsConstructor
public class AcgMetricsService {

    private final AcgWorkMapper workMapper;
    private final AcgHeatLogMapper heatLogMapper;

    /** 全库最高月销量；没有数据时返回 0 */
    public int maxMonthlySales() {
        List<Object> objs = workMapper.selectObjs(Wrappers.<AcgWork>query().select("IFNULL(MAX(monthly_sales), 0)"));
        if (objs == null || objs.isEmpty() || objs.get(0) == null) {
            return 0;
        }
        return objs.get(0) instanceof Number n ? n.intValue() : 0;
    }

    /**
     * 批量取「上一次热度」：每个作品按记录日期倒序，取第二条的 heat_score。
     * 只有一条快照（首次录入）的作品不会出现在结果里，视为无历史。
     */
    public Map<Long, Integer> prevHeat(Collection<Long> workIds) {
        if (workIds == null || workIds.isEmpty()) {
            return Map.of();
        }
        List<AcgHeatLog> logs = heatLogMapper.selectList(Wrappers.<AcgHeatLog>lambdaQuery()
                .in(AcgHeatLog::getWorkId, workIds)
                .orderByDesc(AcgHeatLog::getRecordDate)
                .orderByDesc(AcgHeatLog::getId));

        Map<Long, List<AcgHeatLog>> byWork = logs.stream().collect(Collectors.groupingBy(AcgHeatLog::getWorkId));
        Map<Long, Integer> result = new HashMap<>();
        byWork.forEach((workId, list) -> {
            if (list.size() >= 2) {
                result.put(workId, list.get(1).getHeatScore());
            }
        });
        return result;
    }
}
