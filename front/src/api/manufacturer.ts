import request from './request'
import type { PageResult } from './warehouse'

/* ---------------- 厂商目录（目录维护 → 厂商目录维护） ---------------- */

export interface Manufacturer {
  id: number
  /** 公司 / 厂牌名称 */
  name: string
  /** 公司厂牌 Logo 图片 URL */
  logo?: string | null
  /** 创立时间，yyyy-MM-dd */
  foundedDate?: string | null
  /** 国家 / 地区 */
  country?: string | null
  remark?: string | null
  /** 1 启用 / 0 停用 */
  status: number
  /** 启用 / 停用 */
  statusText: string
  createTime?: string
  updateTime?: string
}

export interface ManufacturerQueryParams {
  page?: number
  size?: number
  /** 关键字，匹配公司名称 / 国家地区 */
  keyword?: string
  /** 国家 / 地区，精确匹配 */
  country?: string
  /** 1 启用 / 0 停用 */
  status?: number
  /** createDesc（默认）/ nameAsc / foundedAsc / foundedDesc */
  sortBy?: string
}

export interface ManufacturerSaveParams {
  name: string
  logo?: string
  /** 留空 / null 表示清空 */
  foundedDate?: string | null
  country?: string
  remark?: string
  /** 编辑时不传则保持原状态 */
  status?: number
}

export interface ManufacturerSummary {
  total: number
  enabled: number
  disabled: number
  countryCount: number
}

export interface ManufacturerOption {
  id: number
  name: string
  logo?: string | null
  country?: string | null
}

export const pageManufacturersApi = (params: ManufacturerQueryParams) =>
  request<PageResult<Manufacturer>>({ url: '/manufacturer/page', method: 'get', params })

export const getManufacturerApi = (id: number) =>
  request<Manufacturer>({ url: `/manufacturer/${id}`, method: 'get' })

export const getManufacturerSummaryApi = () =>
  request<ManufacturerSummary>({ url: '/manufacturer/summary', method: 'get' })

export const getManufacturerCountriesApi = () =>
  request<string[]>({ url: '/manufacturer/countries', method: 'get' })

/** 下拉选项：默认只返回启用厂商，列表筛选可要求带上停用的 */
export const getManufacturerOptionsApi = (params?: { keyword?: string; includeDisabled?: boolean }) =>
  request<ManufacturerOption[]>({ url: '/manufacturer/options', method: 'get', params })

export const createManufacturerApi = (data: ManufacturerSaveParams) =>
  request<number>({ url: '/manufacturer', method: 'post', data })

export const updateManufacturerApi = (id: number, data: ManufacturerSaveParams) =>
  request<void>({ url: `/manufacturer/${id}`, method: 'put', data })

export const deleteManufacturerApi = (id: number) =>
  request<void>({ url: `/manufacturer/${id}`, method: 'delete' })

export const updateManufacturerStatusApi = (id: number, status: number) =>
  request<void>({ url: `/manufacturer/${id}/status`, method: 'put', params: { status } })
