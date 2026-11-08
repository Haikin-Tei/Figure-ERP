import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getMessageSummaryApi, type MessageSummary } from '@/api/message'

/**
 * 消息未读状态。
 * <p>
 * 只负责「有多少未读」这件事：
 * - 右上角菜单的红点、抽屉顶部的统计都读它；
 * - 组件里轮询 refresh()，处理/已读之后再主动刷一次，保证红点立刻消失。
 * <p>
 * 轮询失败不弹错（后端重启是常态），静默保留上一次的数值。
 */
export const useMessageStore = defineStore('message', () => {
  const summary = ref<MessageSummary | null>(null)
  const loading = ref(false)

  const unread = computed(() => summary.value?.unread ?? 0)
  const urgentUnread = computed(() => summary.value?.urgentUnread ?? 0)

  let timer: number | null = null

  async function refresh() {
    loading.value = true
    try {
      summary.value = await getMessageSummaryApi()
    } catch {
      // 静默失败：网络抖动 / 后端重启时不要把错误弹到用户脸上
    } finally {
      loading.value = false
    }
  }

  /** 开始轮询，默认 60 秒一次 */
  function startPolling(intervalMs = 60_000) {
    stopPolling()
    void refresh()
    timer = window.setInterval(() => void refresh(), intervalMs)
  }

  function stopPolling() {
    if (timer !== null) {
      window.clearInterval(timer)
      timer = null
    }
  }

  /** 退出登录时清空，避免下一个人登录先看到上一个人的红点 */
  function reset() {
    stopPolling()
    summary.value = null
  }

  return { summary, loading, unread, urgentUnread, refresh, startPolling, stopPolling, reset }
})
