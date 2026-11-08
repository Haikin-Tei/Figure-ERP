import request from './request'

/** 登录用户信息，字段与后端 UserVO 对应 */
export interface UserInfo {
  id: number
  username: string
  realName: string
  avatar?: string
  role?: string
  roleLabel?: string
  /** 角色板块：系统 / 管理层 / 财务 / 销售 / 物流 / 人事 / 通用 */
  roleGroup?: string
  /** 权限层级：3 管理级 / 2 主管级 / 1 执行级 */
  roleLevel?: number
  status?: number
  intro?: string
  remark?: string
  lastLoginTime?: string
  createTime?: string
}

/** 登录结果：令牌 + 用户信息 */
export interface LoginResult {
  token: string
  tokenType: string
  expiresIn: number
  user: UserInfo
}

export interface LoginParams {
  username: string
  password: string
}

/**
 * 登录。
 * skipAuthRedirect 是给拦截器看的：登录接口返回 401 是「账号密码错」，
 * 不应该被当成「登录过期」再跳一次登录页。
 */
export const loginApi = (data: LoginParams) =>
  request<LoginResult>({
    url: '/auth/login',
    method: 'post',
    data,
    skipAuthRedirect: true
  })

/** 取当前登录用户（刷新页面后恢复登录态） */
export const getMeApi = () => request<UserInfo>({ url: '/auth/me', method: 'get' })

/** 退出登录 */
export const logoutApi = () => request<void>({ url: '/auth/logout', method: 'post' })
