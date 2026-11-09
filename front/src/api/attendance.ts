import request from './request'
import type { PageResult } from './warehouse'
import type { DictItem } from './user'

/* ---------------- 员工考勤（员工权限管理 → 员工考勤记录） ---------------- */

/** 考勤记录行：一名员工某一天的考勤 */
export interface AttendanceRecord {
  id: number
  userId: number
  userName: string
  /** 角色编码，如 SALES_STAFF */
  role?: string
  roleLabel?: string
  /** 考勤日期 YYYY-MM-DD */
  attendDate: string
  /** 上班时间，ISO-8601 */
  checkInTime?: string | null
  /** 下班时间，销售岗为空 */
  checkOutTime?: string | null
  /** 上班状态：N 正常 / L 迟到 */
  checkInStatus: string
  checkInStatusText: string
  /** 下班状态：N 正常 / E 早退 / M 未打卡 / X 免打卡 */
  checkOutStatus: string
  checkOutStatusText: string
  workMinutes?: number | null
  /** 工时（小时，已四舍五入到 1 位小数） */
  workHours?: number | null
  /** 销售岗免下班卡 */
  exemptCheckOut?: boolean
  remark?: string | null
  createTime?: string
}

export interface AttendanceQueryParams {
  page?: number
  size?: number
  keyword?: string
  userId?: number
  checkInStatus?: string
  checkOutStatus?: string
  startDate?: string
  endDate?: string
}

export interface AttendanceSaveParams {
  /** 留空表示当前登录用户；只有主管 / 人事可以代录 */
  userId?: number
  attendDate: string
  checkInTime: string
  checkOutTime?: string | null
  remark?: string
}

export interface AttendanceSummary {
  monthRecords: number
  lateCount: number
  earlyCount: number
  missingCount: number
  staffCount: number
}

export interface AttendanceDicts {
  checkInStatuses: DictItem[]
  checkOutStatuses: DictItem[]
}

export const pageAttendancesApi = (params: AttendanceQueryParams) =>
  request<PageResult<AttendanceRecord>>({ url: '/attendance/page', method: 'get', params })

export const getAttendanceSummaryApi = () =>
  request<AttendanceSummary>({ url: '/attendance/summary', method: 'get' })

export const getAttendanceDictsApi = () =>
  request<AttendanceDicts>({ url: '/attendance/dicts', method: 'get' })

export const createAttendanceApi = (data: AttendanceSaveParams) =>
  request<number>({ url: '/attendance', method: 'post', data })

export const updateAttendanceApi = (id: number, data: AttendanceSaveParams) =>
  request<void>({ url: `/attendance/${id}`, method: 'put', data })

export const deleteAttendanceApi = (id: number) =>
  request<void>({ url: `/attendance/${id}`, method: 'delete' })
