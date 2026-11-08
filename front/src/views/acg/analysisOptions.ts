import type { AcgAnalysis } from '@/api/acg'
import { LEVEL_META } from './acgDict'

/**
 * 「热度 × 销量」四象限散点图。
 * <p>
 * 十字分割线取当前库内的平均热度 / 平均销量，而不是固定阈值：
 * 作品整体水平变化后，象限会自动重算，不会出现「全部挤在右下角」的失真。
 */
export function buildQuadrantOption(a: AcgAnalysis): Record<string, unknown> {
  const avgHeat = Number(a.kpi.avgHeat) || 0
  const avgSales = a.kpi.workCount > 0 ? a.kpi.monthlySalesTotal / a.kpi.workCount : 0

  const series = (['S', 'A', 'B', 'C'] as const).map((level, index) => {
    const meta = LEVEL_META[level]
    const points = a.quadrant
      .filter((q) => q.potentialLevel === level)
      .map((q) => ({ name: q.name, value: [q.heatScore, q.monthlySales, q.name, q.quadrantText] }))

    return {
      name: `${level} 级 · ${meta?.text ?? ''}`,
      type: 'scatter',
      data: points,
      symbolSize: 16,
      itemStyle: { color: meta?.color ?? '#909399', opacity: 0.85 },
      emphasis: { scale: 1.5 },
      // 分割线只挂在第一个系列上，避免重复绘制；
      // label 放在线的两端而不是顶部，否则会和图例、Y 轴标题挤在一起
      markLine:
        index === 0
          ? {
              silent: true,
              symbol: 'none',
              lineStyle: { type: 'dashed', color: '#c0c4cc', width: 1 },
              label: { color: '#909399', fontSize: 11 },
              data: [
                { xAxis: avgHeat, label: { formatter: `平均热度 ${avgHeat}` } },
                { yAxis: avgSales, label: { formatter: `平均月销 ${Math.round(avgSales)}`, position: 'insideEndTop' } }
              ]
            }
          : undefined
    }
  })

  return {
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => {
        const v = p.data?.value ?? []
        return `<b>${v[2] ?? p.name}</b><br/>热度：${v[0]}<br/>月销：${v[1]} 件<br/>${v[3] ?? ''}`
      }
    },
    legend: { top: 0, icon: 'circle', itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 12 } },
    grid: { left: 62, right: 40, top: 46, bottom: 40 },
    xAxis: {
      name: '热度',
      nameLocation: 'middle',
      nameGap: 26,
      min: 0,
      max: 100,
      splitLine: { lineStyle: { type: 'dashed', color: '#f0f0f0' } },
      axisLine: { lineStyle: { color: '#dcdfe6' } }
    },
    yAxis: {
      name: '近30天销量(件)',
      nameLocation: 'middle',
      nameRotate: 90,
      nameGap: 46,
      nameTextStyle: { fontSize: 11 },
      splitLine: { lineStyle: { type: 'dashed', color: '#f0f0f0' } },
      axisLine: { lineStyle: { color: '#dcdfe6' } }
    },
    series
  }
}

/** 类型分布：柱状看销量，折线看作品数量（双 Y 轴，量级差太大） */
export function buildTypeOption(a: AcgAnalysis): Record<string, unknown> {
  const shares = a.typeShares
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0, textStyle: { fontSize: 12 } },
    grid: { left: 56, right: 48, top: 44, bottom: 36 },
    xAxis: {
      type: 'category',
      data: shares.map((s) => s.workTypeText),
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisTick: { show: false }
    },
    yAxis: [
      { type: 'value', name: '月销(件)', splitLine: { lineStyle: { type: 'dashed', color: '#f0f0f0' } } },
      { type: 'value', minInterval: 1, splitLine: { show: false } }
    ],
    series: [
      {
        name: '月销量',
        type: 'bar',
        barWidth: 28,
        data: shares.map((s) => s.monthlySales),
        itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] }
      },
      {
        name: '作品数',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 8,
        data: shares.map((s) => s.count),
        itemStyle: { color: '#e6a23c' },
        lineStyle: { width: 2 }
      }
    ]
  }
}

/** 单个作品的热度趋势折线 */
export function buildTrendOption(points: { recordDate: string; heatScore: number; monthlySales: number }[]): Record<string, unknown> {
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { fontSize: 12 } },
    grid: { left: 48, right: 48, top: 44, bottom: 40 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: points.map((p) => p.recordDate),
      axisLine: { lineStyle: { color: '#dcdfe6' } }
    },
    yAxis: [
      { type: 'value', name: '热度', min: 0, max: 100, splitLine: { lineStyle: { type: 'dashed', color: '#f0f0f0' } } },
      { type: 'value', name: '月销(件)', splitLine: { show: false } }
    ],
    series: [
      {
        name: '热度',
        type: 'line',
        smooth: true,
        symbolSize: 8,
        data: points.map((p) => p.heatScore),
        itemStyle: { color: '#f56c6c' },
        areaStyle: { color: 'rgba(245,108,108,0.12)' }
      },
      {
        name: '月销量',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 6,
        data: points.map((p) => p.monthlySales),
        itemStyle: { color: '#409eff' }
      }
    ]
  }
}
