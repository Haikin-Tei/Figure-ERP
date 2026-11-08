import { computed, ref } from 'vue'
import { getDictsApi, type DictItem, type RoleDictItem } from '@/api/user'

/**
 * 员工模块共用的字典与展示格式化。
 * <p>
 * 字典只拉一次，两个页面共享（模块级 ref），避免来回切换时重复请求。
 */
const roles = ref<RoleDictItem[]>([])
const statuses = ref<DictItem[]>([])
let loaded = false

/** 角色按板块分组（系统 / 管理层 / 财务 / 销售 / 物流 / 人事 / 通用），下拉框分组展示 */
export interface RoleGroup {
  label: string
  options: RoleDictItem[]
}

const roleGroups = computed<RoleGroup[]>(() => {
  const map = new Map<string, RoleDictItem[]>()
  for (const role of roles.value) {
    const group = role.group || '其他'
    const list = map.get(group)
    if (list) {
      list.push(role)
    } else {
      map.set(group, [role])
    }
  }
  return [...map.entries()].map(([label, options]) => ({ label, options }))
})

export function useUserDict() {
  async function loadDicts(force = false) {
    if (loaded && !force) return
    const data = await getDictsApi()
    roles.value = data.roles
    statuses.value = data.statuses
    loaded = true
  }

  return { roles, roleGroups, statuses, loadDicts }
}

/** 角色标签颜色：管理级红、主管级橙、执行级灰 */
export function roleTagType(role?: string, level?: number): 'danger' | 'warning' | 'info' {
  const lv = level ?? roles.value.find((r) => r.code === role)?.level
  if (lv === 3) return 'danger'
  if (lv === 2) return 'warning'
  // 老数据没有层级时按编码兜底
  if (role === 'ADMIN' || role === 'GM') return 'danger'
  if (role === 'MANAGER') return 'warning'
  return 'info'
}

/** 后端返回 ISO 时间，转成 2026-11-01 13:09:39 */
export function formatTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}
