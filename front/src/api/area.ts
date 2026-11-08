import request from './request'
import type { PageResult } from './warehouse'

/* ---------------- 区域档案（库存管理 → 仓库设置 → 区域配置） ---------------- */

export interface Area {
  id: number
  /** 区域编号，如 A；同一仓库内唯一 */
  areaCode: string
  /** 区域名称，如 A区-核心展示柜 */
  areaName: string
  /** 所属仓库 ID */
  warehouseId: number
  /** 所属仓库代码，后端关联查出来的 */
  warehouseCode?: string
  /** 所属仓库名称，后端关联查出来的 */
  warehouseName?: string
  /** 物理位置，如 一层靠窗 */
  location?: string | null
  /** 可容纳货架数 */
  capacity?: number
  /** 该区域下未删除的货架数，后端算好的 */
  shelfCount?: number
  /** A 使用中 / D 停用 */
  status: 'A' | 'D'
  /** 后端翻译好的状态文案 */
  statusText?: string
  remark?: string | null
  sortNo?: number
  createTime?: string
  updateTime?: string
}

export interface AreaQueryParams {
  page?: number
  size?: number
  /** 关键字，匹配区域编号或区域名称 */
  keyword?: string
  warehouseId?: number
  status?: string
}

export interface AreaSaveParams {
  areaCode: string
  areaName: string
  warehouseId: number
  location?: string
  capacity?: number
  status: string
  remark?: string
  sortNo?: number
}

export const pageAreasApi = (params: AreaQueryParams) =>
  request<PageResult<Area>>({ url: '/area/page', method: 'get', params })

export const getAreaApi = (id: number) =>
  request<Area>({ url: `/area/${id}`, method: 'get' })

export const createAreaApi = (data: AreaSaveParams) =>
  request<number>({ url: '/area', method: 'post', data })

export const updateAreaApi = (id: number, data: AreaSaveParams) =>
  request<void>({ url: `/area/${id}`, method: 'put', data })

export const deleteAreaApi = (id: number) =>
  request<void>({ url: `/area/${id}`, method: 'delete' })

export const updateAreaStatusApi = (id: number, status: string) =>
  request<void>({ url: `/area/${id}/status`, method: 'put', params: { status } })
