import request from './request'

/** 分页返回结构，与后端 PageResult 对应 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

/* ---------------- 仓库档案 ---------------- */

export interface Warehouse {
  id: number
  /** 仓库代码，未删除记录中唯一 */
  warehouseCode: string
  warehouseName: string
  warehouseAddress?: string | null
  /** N 正常仓 / Y 退货仓 */
  isReturn: 'N' | 'Y'
  /** 后端翻译好的类型文案 */
  isReturnText?: string
  /** A 使用中 / C 盘库中 / D 停用中 */
  status: 'A' | 'C' | 'D'
  /** 后端翻译好的状态文案 */
  statusText?: string
  remark?: string | null
  sortNo?: number
  createTime?: string
  updateTime?: string
}

export interface WarehouseQueryParams {
  page?: number
  size?: number
  warehouseCode?: string
  warehouseName?: string
  isReturn?: string
  status?: string
}

export interface WarehouseSaveParams {
  warehouseCode: string
  warehouseName: string
  warehouseAddress?: string
  isReturn: string
  status: string
  remark?: string
  sortNo?: number
}

export const pageWarehousesApi = (params: WarehouseQueryParams) =>
  request<PageResult<Warehouse>>({ url: '/warehouse/page', method: 'get', params })

export const getWarehouseApi = (id: number) =>
  request<Warehouse>({ url: `/warehouse/${id}`, method: 'get' })

export const createWarehouseApi = (data: WarehouseSaveParams) =>
  request<number>({ url: '/warehouse', method: 'post', data })

export const updateWarehouseApi = (id: number, data: WarehouseSaveParams) =>
  request<void>({ url: `/warehouse/${id}`, method: 'put', data })

export const deleteWarehouseApi = (id: number) =>
  request<void>({ url: `/warehouse/${id}`, method: 'delete' })

export const updateWarehouseStatusApi = (id: number, status: string) =>
  request<void>({ url: `/warehouse/${id}/status`, method: 'put', params: { status } })
