import request from './request'
import type { PageResult } from './warehouse'

/* ---------------- 库存（库存管理 → 手办列表） ---------------- */

/** 库存列表行：一款手办的库存汇总，可能散布在多个库位 */
export interface StockItem {
  productId: number
  productName: string
  productCode?: string | null
  brand?: string | null
  /** 首图 URL，商品已下架时为空 */
  imageUrl?: string | null
  /** 商品是否已逻辑删除（下架） */
  productDeleted?: boolean
  /** 1 在售 / 0 停用，已下架时为 null */
  productStatus?: number | null
  /** 在售 / 已停用 / 已下架 */
  productStatusText?: string
  /** 在库件数合计 */
  totalQuantity: number
  /** 库存金额合计 */
  totalAmount: number
  /** 加权平均成本单价 */
  avgCost: number
  /** 分布在几个库位 */
  locationCount: number
  lastInboundTime?: string | null
}

/** 库位明细：某款手办在某个仓库 / 货架上具体有多少件 */
export interface StockLocation {
  id: number
  warehouseId: number
  warehouseCode?: string | null
  warehouseName?: string | null
  shelfId?: number | null
  shelfCode?: string | null
  shelfLocation?: string | null
  quantity: number
  avgCost: number
  amount: number
  lastInboundTime?: string | null
}

export interface StockQueryParams {
  page?: number
  size?: number
  /** 关键字，匹配手办名称 / 货号 / 品牌 */
  keyword?: string
  warehouseId?: number
  /** QUANTITY 件数降序（默认）/ AMOUNT 金额降序 / RECENT 最近入库 / NAME 名称升序 */
  sortBy?: string
}

export interface StockSummary {
  /** 在库款式数 */
  skuCount: number
  /** 在库总件数 */
  totalQuantity: number
  /** 库存总金额 */
  totalAmount: number
  /** 涉及仓库数 */
  warehouseCount: number
  /** 占用库位数 */
  locationCount: number
}

export const pageStocksApi = (params: StockQueryParams) =>
  request<PageResult<StockItem>>({ url: '/stock/page', method: 'get', params })

export const getStockSummaryApi = () =>
  request<StockSummary>({ url: '/stock/summary', method: 'get' })

export const getStockLocationsApi = (productId: number) =>
  request<StockLocation[]>({ url: `/stock/${productId}/locations`, method: 'get' })
