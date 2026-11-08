/** ACG 作品的字典与展示元数据，前后端码值保持一致（见后端 AcgEnums） */

export interface Option {
  value: string
  label: string
}

/** 作品类型 */
export const WORK_TYPES: Option[] = [
  { value: 'ANIME', label: '动画' },
  { value: 'MANGA', label: '漫画' },
  { value: 'GAME', label: '游戏' },
  { value: 'NOVEL', label: '轻小说' },
  { value: 'MOVIE', label: '剧场版' },
  { value: 'OTHER', label: '其他' }
]

/** 连载状态 */
export const RELEASE_STATUSES: Option[] = [
  { value: 'ONGOING', label: '连载中' },
  { value: 'FINISHED', label: '已完结' },
  { value: 'UPCOMING', label: '待播出' }
]

/** 排序选项 */
export const SORT_OPTIONS: Option[] = [
  { value: 'heatDesc', label: '热度从高到低' },
  { value: 'heatAsc', label: '热度从低到高' },
  { value: 'salesDesc', label: '月销量从高到低' },
  { value: 'salesAsc', label: '月销量从低到高' },
  { value: 'yearDesc', label: '发行年份从新到旧' },
  { value: 'createDesc', label: '最近录入优先' }
]

/** 潜力等级展示元数据 */
export const LEVEL_META: Record<string, { color: string; text: string; desc: string }> = {
  S: { color: '#f56c6c', text: '重点主推', desc: '热度与销量双高，趋势向上' },
  A: { color: '#e6a23c', text: '积极备货', desc: '整体表现好，可以加大投入' },
  B: { color: '#409eff', text: '常规跟进', desc: '表现中等，按常规节奏备货' },
  C: { color: '#909399', text: '谨慎 / 淘汰', desc: '热度与销量偏弱，控制库存' }
}

/** 类型 → el-tag 的 type，保证列表里类型颜色一眼可分 */
export function tagType(workType: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const colors: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    ANIME: 'primary',
    MANGA: 'success',
    GAME: 'warning',
    NOVEL: 'info',
    MOVIE: 'danger',
    OTHER: 'info'
  }
  return colors[workType] ?? 'info'
}

export function typeLabel(value?: string | null): string {
  return WORK_TYPES.find((t) => t.value === value)?.label ?? value ?? '—'
}

export function releaseStatusLabel(value?: string | null): string {
  return RELEASE_STATUSES.find((t) => t.value === value)?.label ?? value ?? '—'
}

/** 千分位 */
export function formatNumber(value?: number | null): string {
  if (value === null || value === undefined) return '0'
  return value.toLocaleString('zh-CN')
}
