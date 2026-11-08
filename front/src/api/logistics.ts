import request from './request'

/** 分页返回结构，与后端 PageResult 对应 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

/* ---------------- 字典 ---------------- */

export interface SupplierDict {
  code: string
  label: string
  baseFirstPrice: number
  baseAdditionalPrice: number
}

export interface ProvinceDict {
  name: string
  zone: string
  zoneLabel: string
  cities: string[]
  municipality: boolean
}

export interface OriginDict {
  province: string
  city: string
  district?: string
  label: string
}

export interface ZoneDict {
  code: string
  label: string
  firstOffset: number
  additionalOffset: number
  daysOffset: number
}

export interface LogisticsDict {
  suppliers: SupplierDict[]
  provinces: ProvinceDict[]
  origins: OriginDict[]
  zones: ZoneDict[]
}

/** 物流字典：供应商、省份（含城市与区域）、已配置出发地 */
export const getLogisticsDictApi = () =>
  request<LogisticsDict>({ url: '/logistics/dicts', method: 'get' })

/* ---------------- 价格 ---------------- */

export interface LogisticsPrice {
  id: number
  supplierCode: string
  supplierName: string
  originProvince: string
  originCity: string
  originDistrict?: string
  originLabel: string
  destProvince: string
  destCity?: string | null
  destLabel: string
  zoneLabel: string
  firstWeight: number
  firstPrice: number
  additionalWeight: number
  additionalPrice: number
  priceLabel: string
  estimatedDays?: number
  status: number
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface LogisticsPriceQueryParams {
  page?: number
  size?: number
  supplierCode?: string
  originProvince?: string
  originCity?: string
  destProvince?: string
  destCity?: string
  keyword?: string
  status?: number
}

export interface LogisticsPriceSaveParams {
  supplierCode: string
  originProvince: string
  originCity: string
  originDistrict?: string
  destProvince: string
  destCity?: string
  firstWeight: number
  firstPrice: number
  additionalWeight: number
  additionalPrice: number
  estimatedDays?: number
  status: number
  remark?: string
}

export interface BatchPriceItem {
  destProvince: string
  destCity?: string
  firstWeight: number
  firstPrice: number
  additionalWeight: number
  additionalPrice: number
  estimatedDays?: number
  status: number
  remark?: string
}

export interface BatchPriceParams {
  supplierCode: string
  originProvince: string
  originCity: string
  originDistrict?: string
  items: BatchPriceItem[]
}

export const pagePricesApi = (params: LogisticsPriceQueryParams) =>
  request<PageResult<LogisticsPrice>>({ url: '/logistics/prices', method: 'get', params })

export const getPriceApi = (id: number) =>
  request<LogisticsPrice>({ url: `/logistics/prices/${id}`, method: 'get' })

export const createPriceApi = (data: LogisticsPriceSaveParams) =>
  request<number>({ url: '/logistics/prices', method: 'post', data })

export const updatePriceApi = (id: number, data: LogisticsPriceSaveParams) =>
  request<void>({ url: `/logistics/prices/${id}`, method: 'put', data })

export const deletePriceApi = (id: number) =>
  request<void>({ url: `/logistics/prices/${id}`, method: 'delete' })

export const updatePriceStatusApi = (id: number, status: number) =>
  request<void>({ url: `/logistics/prices/${id}/status`, method: 'put', params: { status } })

/** 批量保存某供应商发往全国的价格，返回影响条数 */
export const batchSavePricesApi = (data: BatchPriceParams) =>
  request<number>({ url: '/logistics/prices/batch', method: 'post', data })

/* ---------------- 报价 ---------------- */

export interface QuoteItem {
  supplierCode: string
  supplierName: string
  totalPrice: number
  firstWeight: number
  firstPrice: number
  additionalWeight: number
  additionalPrice: number
  additionalUnits: number
  formula: string
  estimatedDays?: number
  cheapest: boolean
  fastest: boolean
  remark?: string
}

export interface QuoteResult {
  originLabel: string
  destLabel: string
  zoneLabel: string
  weight: number
  items: QuoteItem[]
  count: number
  lowestPrice?: number
  lowestSupplier?: string
  fastestDays?: number
  advice?: string
}

export interface QuoteParams {
  originProvince?: string
  originCity?: string
  originDistrict?: string
  destProvince: string
  destCity?: string
  weight: number
}

/** 运费报价：给定路线与重量，返回各供应商对比 */
export const quoteApi = (params: QuoteParams) =>
  request<QuoteResult>({ url: '/logistics/quote', method: 'get', params })
