import request from './request'
import type { PageResult } from './warehouse'
import type { DictItem } from './user'

/* ---------------- 员工加班申请（员工权限管理 → 员工加班申请） ---------------- */

/** 加班单：结构、权限开关与请假单一致，只是类型和时长单位不同 */
export interface OvertimeApply {
  id: number
  applyNo: string
  userId: number
  userName: string
  role?: string
  roleLabel?: string
  /** 加班类型编码：W 工作日 / O 休息日 / H 节假日 */
  overtimeType: string
  overtimeTypeText: string
  startTime: string
  endTime: string
  /** 加班时长（小时） */
  durationHours: number
  reason: string
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

export interface OvertimeQueryParams {
  page?: number
  size?: number
  keyword?: string
  userId?: number
  overtimeType?: string
  status?: string
  startDate?: string
  endDate?: string
}

export interface OvertimeSaveParams {
  userId?: number
  overtimeType: string
  startTime: string
  endTime: string
  reason: string
}

export interface OvertimeSummary {
  pendingCount: number
  monthApprovedCount: number
  monthHours: number
  monthStaffCount: number
}

export interface OvertimeDicts {
  overtimeTypes: DictItem[]
  statuses: DictItem[]
}

export const pageOvertimesApi = (params: OvertimeQueryParams) =>
  request<PageResult<OvertimeApply>>({ url: '/overtime/page', method: 'get', params })

export const getOvertimeSummaryApi = () =>
  request<OvertimeSummary>({ url: '/overtime/summary', method: 'get' })

export const getOvertimeDictsApi = () =>
  request<OvertimeDicts>({ url: '/overtime/dicts', method: 'get' })

export const createOvertimeApi = (data: OvertimeSaveParams) =>
  request<number>({ url: '/overtime', method: 'post', data })

export const updateOvertimeApi = (id: number, data: OvertimeSaveParams) =>
  request<void>({ url: `/overtime/${id}`, method: 'put', data })

export const cancelOvertimeApi = (id: number) =>
  request<void>({ url: `/overtime/${id}/cancel`, method: 'put' })

export const approveOvertimeApi = (id: number, data: { result: string; remark?: string }) =>
  request<void>({ url: `/overtime/${id}/approve`, method: 'put', data })

export const deleteOvertimeApi = (id: number) =>
  request<void>({ url: `/overtime/${id}`, method: 'delete' })
