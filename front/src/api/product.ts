import request from './request'

/** 分页返回结构，与后端 PageResult 对应 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

/* ---------------- 手办商品 ---------------- */

export interface FigureProduct {
  id: number
  name: string
  code?: string | null
  /** 厂商 ID（厂商目录），为空表示未关联 */
  manufacturerId?: number | null
  /** 品牌 / 厂商名称，已关联时取厂商目录当前名称 */
  brand?: string | null
  /** 厂商 Logo，已关联时返回 */
  manufacturerLogo?: string | null
  series?: string | null
  characterName?: string | null
  material?: string | null
  lengthCm?: number | null
  widthCm?: number | null
  heightCm?: number | null
  /** 带盒重量（g），含包装盒 */
  weightWithBox: number
  /** 商品图片 URL 列表，3~4 张 */
  images: string[]
  /** 规格文案：长×宽×高 cm / 带盒 xxx g */
  specLabel?: string
  remark?: string | null
  status: number
  createTime?: string
  updateTime?: string
}

export interface FigureProductQueryParams {
  page?: number
  size?: number
  name?: string
  code?: string
  /** 厂商 ID，精确过滤 */
  manufacturerId?: number
  /** 品牌名称模糊，兼容未关联厂商的老数据 */
  brand?: string
  series?: string
  characterName?: string
  status?: number
}

export interface FigureProductSaveParams {
  name: string
  code?: string
  /** 厂商 ID；传了则品牌以厂商目录为准 */
  manufacturerId?: number | null
  /** 未选厂商时作为自由文本品牌保存 */
  brand?: string
  series?: string
  characterName?: string
  material?: string
  lengthCm?: number
  widthCm?: number
  heightCm?: number
  /** 带盒重量（g） */
  weightWithBox: number
  /** 商品图片 URL 列表，3~4 张 */
  images: string[]
  status: number
  remark?: string
}

export const pageProductsApi = (params: FigureProductQueryParams) =>
  request<PageResult<FigureProduct>>({ url: '/product/page', method: 'get', params })

export const getProductApi = (id: number) =>
  request<FigureProduct>({ url: `/product/${id}`, method: 'get' })

export const createProductApi = (data: FigureProductSaveParams) =>
  request<number>({ url: '/product', method: 'post', data })

export const updateProductApi = (id: number, data: FigureProductSaveParams) =>
  request<void>({ url: `/product/${id}`, method: 'put', data })

export const deleteProductApi = (id: number) =>
  request<void>({ url: `/product/${id}`, method: 'delete' })

export const updateProductStatusApi = (id: number, status: number) =>
  request<void>({ url: `/product/${id}/status`, method: 'put', params: { status } })
