import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getMeApi, loginApi, logoutApi, type LoginParams, type UserInfo } from '@/api/auth'
import { clearToken, getToken, setToken } from '@/utils/auth'

/**
 * 登录状态。
 * <p>
 * token 存在 localStorage，userInfo 需要每次刷新后重新拉取（不落盘，
 * 避免本地缓存被人改掉后冒充别人 —— 真正的身份以服务端令牌为准）。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken() ?? '')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  /** 右上角显示的名字：优先姓名，没有就用账号 */
  const displayName = computed(() => userInfo.value?.realName || userInfo.value?.username || '')

  /** 登录：成功即写入令牌和用户信息 */
  async function login(params: LoginParams) {
    const result = await loginApi(params)
    token.value = result.token
    setToken(result.token)
    userInfo.value = result.user
    return result
  }

  /** 刷新页面后恢复用户信息，令牌失效会抛错，由调用方处理 */
  async function fetchUserInfo() {
    if (!token.value) return null
    const info = await getMeApi()
    userInfo.value = info
    return info
  }

  /** 退出：先通知服务端（失败也不影响本地清理） */
  async function logout() {
    try {
      if (token.value) await logoutApi()
    } catch {
      // 令牌已过期时退出接口必然失败，忽略即可
    }
    reset()
  }

  /** 只清本地状态 */
  function reset() {
    token.value = ''
    userInfo.value = null
    clearToken()
  }

  return { token, userInfo, isLoggedIn, displayName, login, fetchUserInfo, logout, reset }
})
