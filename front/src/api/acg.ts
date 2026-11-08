import request from './request'

/** 分页返回结构，与后端 PageResult 对应 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

/* ---------------- ACG 作品 ---------------- */

export interface AcgWork {
  id: number
  workCode?: string | null
  name: string
  originalName?: string | null
  workType: string
  workTypeText: string
  ipOwner?: string | null
  studio?: string | null
  author?: string | null
  releaseYear?: number | null
  releaseStatus: string
  releaseStatusText: string
  /** 当前热度 0-100 */
  heatScore: number
  /** 近 30 天销量 */
  monthlySales: number
  /** 累计销量 */
  totalSales: number
  tags: string[]
  coverImage?: string | null
  heatUpdatedAt?: string | null
  remark?: string | null
  status: number
  /** 热度环比涨跌幅（%），如 10.3 表示上涨 10.3% */
  trendRate: number
  /** 上升 / 下降 / 持平 */
  trendText: string
  /** 选品潜力分 0-100 */
  potentialScore: number
  /** S / A / B / C */
  potentialLevel: string
  potentialLevelText: string
  createTime?: string
  updateTime?: string
}

export interface AcgWorkQueryParams {
  page?: number
  size?: number
  name?: string
  workCode?: string
  workType?: string
  releaseStatus?: string
  tag?: string
  releaseYear?: number
  minHeat?: number
  maxHeat?: number
  status?: number
  sortBy?: string
}

export interface AcgWorkSaveParams {
  workCode?: string
  name: string
  originalName?: string
  workType: string
  ipOwner?: string
  studio?: string
  author?: string
  releaseYear?: number
  releaseStatus?: string
  heatScore?: number
  monthlySales?: number
  totalSales?: number
  tags?: string[]
  coverImage?: string
  remark?: string
  status: number
}

export interface AcgHeatRecordParams {
  heatScore: number
  monthlySales?: number
  recordDate?: string
  remark?: string
}

export interface AcgHeatPoint {
  recordDate: string
  heatScore: number
  monthlySales: number
  remark?: string | null
}

export interface AcgOption {
  id: number
  name: string
  workType: string
  workTypeText: string
  releaseYear?: number | null
  heatScore: number
}

export interface AcgAnalysis {
  kpi: {
    workCount: number
    selectableCount: number
    avgHeat: number
    monthlySalesTotal: number
    avgPotential: number
    levelSCount: number
    levelACount: number
    levelBCount: number
    levelCCount: number
    topTypeText: string
    topTypeCount: number
  }
  ranking: AcgRankItem[]
  quadrant: AcgQuadrantPoint[]
  typeShares: AcgTypeShare[]
  insights: string[]
  formula: string
}

export interface AcgRankItem {
  id: number
  name: string
  coverImage?: string | null
  workType: string
  workTypeText: string
  heatScore: number
  monthlySales: number
  potentialScore: number
  potentialLevel: string
  trendRate: number
}

export interface AcgQuadrantPoint {
  id: number
  name: string
  heatScore: number
  monthlySales: number
  potentialLevel: string
  quadrant: 'STAR' | 'HEAT_ONLY' | 'SALES_ONLY' | 'LOW'
  quadrantText: string
}

export interface AcgTypeShare {
  workType: string
  workTypeText: string
  count: number
  monthlySales: number
  avgHeat: number
}

export const pageAcgWorksApi = (params: AcgWorkQueryParams) =>
  request<PageResult<AcgWork>>({ url: '/acg/page', method: 'get', params })

export const getAcgWorkApi = (id: number) =>
  request<AcgWork>({ url: `/acg/${id}`, method: 'get' })

export const createAcgWorkApi = (data: AcgWorkSaveParams) =>
  request<number>({ url: '/acg', method: 'post', data })

export const updateAcgWorkApi = (id: number, data: AcgWorkSaveParams) =>
  request<void>({ url: `/acg/${id}`, method: 'put', data })

export const deleteAcgWorkApi = (id: number) =>
  request<void>({ url: `/acg/${id}`, method: 'delete' })

export const updateAcgStatusApi = (id: number, status: number) =>
  request<void>({ url: `/acg/${id}/status`, method: 'put', params: { status } })

export const recordAcgHeatApi = (id: number, data: AcgHeatRecordParams) =>
  request<void>({ url: `/acg/${id}/heat`, method: 'post', data })

export const getAcgTrendApi = (id: number, days = 90) =>
  request<AcgHeatPoint[]>({ url: `/acg/${id}/trend`, method: 'get', params: { days } })

export const getAcgAnalysisApi = () =>
  request<AcgAnalysis>({ url: '/acg/analysis', method: 'get' })

export const getAcgOptionsApi = (keyword?: string) =>
  request<AcgOption[]>({ url: '/acg/options', method: 'get', params: { keyword } })

export const getAcgTypesApi = () =>
  request<{ workTypes: Record<string, string>; releaseStatuses: Record<string, string> }>({
    url: '/acg/types',
    method: 'get'
  })
