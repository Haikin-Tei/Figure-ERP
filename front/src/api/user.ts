import request from './request'
import type { UserInfo } from './auth'

/** 分页返回结构，与后端 PageResult 对应 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  pages: number
}

/** 员工列表查询条件 */
export interface UserQueryParams {
  page?: number
  size?: number
  keyword?: string
  role?: string
  status?: number
}

/** 新增 / 编辑提交的数据 */
export interface UserSaveParams {
  username?: string
  realName: string
  password?: string
  role: string
  status: number
  intro?: string
  remark?: string
}

export interface DictItem {
  code: string | number
  label: string
}

/** 角色字典项：比普通字典多了分组与权限层级 */
export interface RoleDictItem extends DictItem {
  group?: string
  level?: number
}

export interface DictData {
  roles: RoleDictItem[]
  statuses: DictItem[]
}

/** 分页查询员工 */
export const pageUsersApi = (params: UserQueryParams) =>
  request<PageResult<UserInfo>>({ url: '/system/users', method: 'get', params })

/** 员工详情 */
export const getUserApi = (id: number) =>
  request<UserInfo>({ url: `/system/users/${id}`, method: 'get' })

/** 新增员工，返回新 id */
export const createUserApi = (data: UserSaveParams) =>
  request<number>({ url: '/system/users', method: 'post', data })

/** 编辑员工（账号不可改） */
export const updateUserApi = (id: number, data: UserSaveParams) =>
  request<void>({ url: `/system/users/${id}`, method: 'put', data })

/** 删除员工（逻辑删除） */
export const deleteUserApi = (id: number) =>
  request<void>({ url: `/system/users/${id}`, method: 'delete' })

/** 重置密码 */
export const resetUserPasswordApi = (id: number, newPassword: string) =>
  request<void>({ url: `/system/users/${id}/password`, method: 'put', data: { newPassword } })

/** 启用 / 停用 */
export const updateUserStatusApi = (id: number, status: number) =>
  request<void>({ url: `/system/users/${id}/status`, method: 'put', params: { status } })

/** 字典：角色 / 状态 */
export const getDictsApi = () => request<DictData>({ url: '/system/dicts', method: 'get' })
