import request from './request'
import type { PageResult } from './warehouse'
import type { DictItem } from './user'

/* ---------------- 员工请假申请（员工权限管理 → 员工请假申请） ---------------- */

/** 请假单：can* 开关由后端按当前用户算好，前端直接照着渲染按钮 */
export interface LeaveApply {
  id: number
  applyNo: string
  userId: number
  userName: string
  role?: string
  roleLabel?: string
  /** 请假类型编码：A 事假 / B 病假 / C 年假 / D 调休 / E 婚假 / F 产假 / G 其他 */
  leaveType: string
  leaveTypeText: string
  startTime: string
  endTime: string
  durationDays: number
  reason: string
  /** P 待审批 / A 已通过 / R 已驳回 / C 已撤销 */
  status: string
  statusText: string
  approverName?: string | null
  approveTime?: string | null
  approveRemark?: string | null
  canApprove?: boolean
  canCancel?: boolean
  canEdit?: boolean
  canDelete?: boolean
  createTime?: string
}

export interface LeaveQueryParams {
  page?: number
  size?: number
  keyword?: string
  userId?: number
  leaveType?: string
  status?: string
  startDate?: string
  endDate?: string
}

export interface LeaveSaveParams {
  userId?: number
  leaveType: string
  startTime: string
  endTime: string
  reason: string
}

export interface LeaveSummary {
  pendingCount: number
  monthApprovedCount: number
  monthDays: number
  monthStaffCount: number
}

export interface LeaveDicts {
  leaveTypes: DictItem[]
  statuses: DictItem[]
}

export const pageLeavesApi = (params: LeaveQueryParams) =>
  request<PageResult<LeaveApply>>({ url: '/leave/page', method: 'get', params })

export const getLeaveSummaryApi = () => request<LeaveSummary>({ url: '/leave/summary', method: 'get' })

export const getLeaveDictsApi = () => request<LeaveDicts>({ url: '/leave/dicts', method: 'get' })

export const createLeaveApi = (data: LeaveSaveParams) =>
  request<number>({ url: '/leave', method: 'post', data })

export const updateLeaveApi = (id: number, data: LeaveSaveParams) =>
  request<void>({ url: `/leave/${id}`, method: 'put', data })

export const cancelLeaveApi = (id: number) =>
  request<void>({ url: `/leave/${id}/cancel`, method: 'put' })

export const approveLeaveApi = (id: number, data: { result: string; remark?: string }) =>
  request<void>({ url: `/leave/${id}/approve`, method: 'put', data })

export const deleteLeaveApi = (id: number) =>
  request<void>({ url: `/leave/${id}`, method: 'delete' })
