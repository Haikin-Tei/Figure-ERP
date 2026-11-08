/**
 * 登录令牌的本地存储。
 * <p>
 * 单独抽一个文件，避免 request.ts 和 store 互相 import 造成循环依赖。
 * 用 localStorage 而不是 sessionStorage：关掉浏览器再打开仍然保持登录。
 */
const TOKEN_KEY = 'figure_erp_token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}
