import request from './request'
import type { PageResult } from './warehouse'

/* ---------------- 入库记录（库存管理 → 入库记录） ---------------- */

/** 入库类型：P 采购 / R 退货 / T 调拨 / O 其他 */
export type InboundTypeCode = 'P' | 'R' | 'T' | 'O'

/** 状态：D 待入库 / C 已入库 / X 已作废 */
export type InboundStatusCode = 'D' | 'C' | 'X'

export interface InboundItem {
  id?: number
  productId: number
  /** 手办名称（入库时的快照） */
  productName: string
  /** 货号（入库时的快照） */
  productCode?: string | null
  quantity: number
  unitPrice?: number | null
  /** 小计金额，后端计算 */
  amount?: number
  remark?: string | null
  sortNo?: number
}

export interface InboundOrder {
  id: number
  inboundNo: string
  inboundType: InboundTypeCode
  inboundTypeText?: string
  warehouseId: number
  warehouseCode?: string
  warehouseName?: string
  shelfId?: number | null
  shelfCode?: string | null
  supplier?: string | null
  status: InboundStatusCode
  statusText?: string
  totalQuantity: number
  totalAmount: number
  /** 明细行数（几款手办） */
  itemCount?: number
  /** 手办摘要，如「初音未来 雪未来 等 3 款」 */
  productSummary?: string
  inboundTime?: string
  operator?: string | null
  remark?: string | null
  createTime?: string
  updateTime?: string
  /** 手办明细，仅详情接口返回 */
  items?: InboundItem[]
}

export interface InboundQueryParams {
  page?: number
  size?: number
  /** 关键字，匹配单号 / 供应商 / 经办人 / 手办名称 */
  keyword?: string
  warehouseId?: number
  status?: string
  inboundType?: string
  /** yyyy-MM-dd */
  startDate?: string
  /** yyyy-MM-dd */
  endDate?: string
}

export interface InboundItemParams {
  productId: number
  quantity: number
  unitPrice?: number | null
  remark?: string
}

export interface InboundSaveParams {
  /** 留空由后端按 RK + 日期 + 序号自动生成 */
  inboundNo?: string
  inboundType: string
  warehouseId: number
  shelfId?: number | null
  supplier?: string
  /** yyyy-MM-dd HH:mm:ss */
  inboundTime?: string
  operator?: string
  remark?: string
  items: InboundItemParams[]
}

export interface InboundSummary {
  /** 待入库单数（全量） */
  pendingCount: number
  /** 本月已入库单数 */
  monthOrderCount: number
  /** 本月已入库件数 */
  monthQuantity: number
  /** 本月已入库金额 */
  monthAmount: number
}

export const pageInboundsApi = (params: InboundQueryParams) =>
  request<PageResult<InboundOrder>>({ url: '/inbound/page', method: 'get', params })

export const getInboundApi = (id: number) =>
  request<InboundOrder>({ url: `/inbound/${id}`, method: 'get' })

export const getInboundSummaryApi = () =>
  request<InboundSummary>({ url: '/inbound/summary', method: 'get' })

export const createInboundApi = (data: InboundSaveParams) =>
  request<number>({ url: '/inbound', method: 'post', data })

export const updateInboundApi = (id: number, data: InboundSaveParams) =>
  request<void>({ url: `/inbound/${id}`, method: 'put', data })

export const deleteInboundApi = (id: number) =>
  request<void>({ url: `/inbound/${id}`, method: 'delete' })

export const confirmInboundApi = (id: number) =>
  request<void>({ url: `/inbound/${id}/confirm`, method: 'put' })

export const cancelInboundApi = (id: number) =>
  request<void>({ url: `/inbound/${id}/cancel`, method: 'put' })
