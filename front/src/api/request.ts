import axios, { type AxiosRequestConfig, type AxiosInstance } from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearToken } from '@/utils/auth'

/** 后端统一响应结构，和后端 com.figureerp.common.result.Result 一一对应 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 为 true 时，遇到 code=401 不自动跳登录页（登录接口自己用） */
    skipAuthRedirect?: boolean
  }
}

/**
 * 全局唯一的 axios 实例。
 * baseURL 用相对路径 '/api'，由 vite.config.ts 的代理转发到后端 8080，
 * 这样开发期不存在跨域，打包部署时也只需要让 Nginx 转发 /api 即可。
 */
const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截：统一带上登录令牌
service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

/** 防止并发请求同时 401 时弹多次提示、跳多次页面 */
let redirecting = false

/**
 * 令牌失效的统一处理：清掉本地凭证 → 提示 → 带着原地址跳登录页，
 * 登录成功后可以直接回到刚才那一页。
 */
async function handleUnauthorized(message?: string) {
  if (redirecting) return
  redirecting = true
  try {
    clearToken()
    ElMessage.warning(message || '登录已过期，请重新登录')

    // 动态引入，避免 request ← store ← api ← request 的循环依赖
    const [{ default: router }, { useUserStore }] = await Promise.all([
      import('@/router'),
      import('@/stores/user')
    ])
    useUserStore().reset()

    const current = router.currentRoute.value
    await router.replace({
      path: '/login',
      query: current.path === '/login' ? {} : { redirect: current.fullPath }
    })
  } finally {
    // 留一点时间窗口，避免同一批并发请求反复触发
    setTimeout(() => {
      redirecting = false
    }, 300)
  }
}

// 响应拦截：把 { code, message, data } 这层壳剥掉，业务代码直接拿 data
service.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResult
    // 非统一结构的响应（比如导出文件）原样返回
    if (!res || typeof res.code !== 'number') {
      return response.data
    }
    if (res.code === 200) {
      // 这里故意返回 data 而不是整包，调用方 await 到的就是纯数据
      return res.data as never
    }

    if (res.code === 401 && !response.config.skipAuthRedirect) {
      handleUnauthorized(res.message)
    } else {
      ElMessage.error(res.message || '请求失败')
    }
    return Promise.reject(new Error(res.message || `业务异常 code=${res.code}`))
  },
  (error) => {
    // 后端没启动 / 代理没转发时会走到这里
    const isNetworkError = !error.response
    const message = isNetworkError
      ? '无法连接后端服务，请确认 server 已启动（默认 8080）'
      : `请求失败（HTTP ${error.response.status}）`
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

/**
 * 统一的请求方法：返回值已经是剥壳后的 data，
 * 用法：const info = await request<HealthInfo>({ url: '/system/health' })
 */
export default function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  // 响应拦截里已经把 { code, message, data } 剥掉了，实际 resolve 的就是 data，
  // 这里用断言把 axios 的原始返回类型还原成业务类型
  return service.request(config) as unknown as Promise<T>
}

export { service }
