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
  brand?: string | null
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
  brand?: string
  series?: string
  characterName?: string
  status?: number
}

export interface FigureProductSaveParams {
  name: string
  code?: string
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
