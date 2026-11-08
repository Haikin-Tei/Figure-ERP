import type { UserInfo } from '@/api/auth'

/**
 * 角色权限层级，与后端 UserRole.LEVEL_* 一一对应：
 *   3 管理级（管理员 / 总经理）→ 可维护员工账号 + 全部业务
 *   2 主管级（各部门主管）    → 可维护业务数据
 *   1 执行级（各部门专员）    → 只读业务数据
 */
export const ROLE_LEVEL_ADMIN = 3
export const ROLE_LEVEL_MANAGER = 2

/** 旧账号 / 老令牌没有 roleLevel 时的兜底映射 */
const FALLBACK_LEVEL: Record<string, number> = {
  ADMIN: ROLE_LEVEL_ADMIN,
  GM: ROLE_LEVEL_ADMIN,
  MANAGER: ROLE_LEVEL_MANAGER
}

/** 当前用户角色的权限层级；取不到用户信息时按最低权限处理 */
export function roleLevel(user?: UserInfo | null): number {
  if (!user) return 1
  if (typeof user.roleLevel === 'number') return user.roleLevel
  return FALLBACK_LEVEL[user.role ?? ''] ?? 1
}

/** 主管级及以上：可以新增 / 编辑 / 删除业务数据 */
export function canManageBusiness(user?: UserInfo | null): boolean {
  return roleLevel(user) >= ROLE_LEVEL_MANAGER
}

/** 管理级：可以维护员工账号 */
export function isAdminRole(user?: UserInfo | null): boolean {
  return roleLevel(user) >= ROLE_LEVEL_ADMIN
}

/* ---------------- 首页归属 ---------------- */

/**
 * 「数据概览」是经营数据看板（库存、入库、订单、销售额），
 * 只有看这些指标的角色才把它当首页：总经理、销售经理。
 * 其他人的首页是「工作台」（今日工作计划 + 待办事项）。
 */
export const DASHBOARD_HOME_ROLES = ['GM', 'SALES_MANAGER']

/**
 * 可以把「数据概览」当首页的角色。
 * <p>注意这里只认具体的角色编码，不按 level 判断：
 * 财务/物流/人事主管同样是主管级，但他们的首页应该是自己的待办，而不是销售看板。
 */
export function isDashboardHome(user?: UserInfo | null): boolean {
  return DASHBOARD_HOME_ROLES.includes(user?.role ?? '')
}

/**
 * 能看到「数据概览」这个页面（左侧菜单入口 + 直接访问 /dashboard）。
 * <p>比「当首页」宽一点：管理员也保留入口，方便核对数据、验收功能；
 * 想让管理员也看不到，把 ADMIN 从下面的判断里去掉即可。
 */
export function canViewDashboard(user?: UserInfo | null): boolean {
  return isDashboardHome(user) || user?.role === 'ADMIN'
}

/** 登录后应该落到哪个首页 */
export function homePathFor(user?: UserInfo | null): string {
  return isDashboardHome(user) ? '/dashboard' : '/home'
}
