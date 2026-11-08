import request from './request'

/** 后端 /api/system/health 返回结构 */
export interface HealthInfo {
  status: string
  app: string
  version: string
  javaVersion: string
  serverTime: string
}

/** 后端 /api/system/db 返回结构 */
export interface DbHealthInfo {
  connected: boolean
  latencyMs: number
  product?: string
  productVersion?: string
  driverVersion?: string
  database?: string
  tableCount?: number
  pool?: {
    active: number
    idle: number
    total: number
    maxPoolSize: number
  }
  error?: string
}

/** 服务存活检查（不查库，很轻） */
export const getHealth = () => request<HealthInfo>({ url: '/system/health', method: 'get' })

/** 数据库连通检查（会真实取一次连接） */
export const getDbHealth = () => request<DbHealthInfo>({ url: '/system/db', method: 'get' })
