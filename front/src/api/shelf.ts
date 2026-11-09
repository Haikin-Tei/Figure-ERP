import request from './request'
import type { PageResult } from './warehouse'

/* ---------------- 货架档案（库存管理 → 仓库设置 → 货架管理） ---------------- */

export interface Shelf {
  id: number
  /** 货架编号，同一仓库未删除记录中唯一 */
  shelfCode: string
  /** 所属仓库 ID */
  warehouseId: number
  /** 所属仓库代码，后端关联查出来的 */
  warehouseCode?: string
  /** 所属仓库名称，后端关联查出来的 */
  warehouseName?: string
  /** 库位信息，如 01区-1层 */
  location?: string | null
  /** A 正常 / B 维护中 / D 停用 */
  status: 'A' | 'B' | 'D'
  /** 后端翻译好的状态文案 */
  statusText?: string
  /** 状态描述 / 备注 */
  remark?: string | null
  sortNo?: number
  createTime?: string
  updateTime?: string
}

export interface ShelfQueryParams {
  page?: number
  size?: number
  /** 关键字，匹配货架编号或库位信息 */
  keyword?: string
  warehouseId?: number
  status?: string
}

export interface ShelfSaveParams {
  shelfCode: string
  warehouseId: number
  location?: string
  status: string
  remark?: string
  sortNo?: number
}

export const pageShelvesApi = (params: ShelfQueryParams) =>
  request<PageResult<Shelf>>({ url: '/shelf/page', method: 'get', params })

export const getShelfApi = (id: number) =>
  request<Shelf>({ url: `/shelf/${id}`, method: 'get' })

export const createShelfApi = (data: ShelfSaveParams) =>
  request<number>({ url: '/shelf', method: 'post', data })

export const updateShelfApi = (id: number, data: ShelfSaveParams) =>
  request<void>({ url: `/shelf/${id}`, method: 'put', data })

export const deleteShelfApi = (id: number) =>
  request<void>({ url: `/shelf/${id}`, method: 'delete' })

export const updateShelfStatusApi = (id: number, status: string) =>
  request<void>({ url: `/shelf/${id}/status`, method: 'put', params: { status } })
