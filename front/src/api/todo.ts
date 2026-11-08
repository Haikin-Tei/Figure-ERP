import request from './request'
import type { PageResult } from './warehouse'

/** 优先级：H 高 / N 普通 / L 低 */
export type TodoPriority = 'H' | 'N' | 'L'

/** 状态：P 待办 / D 已完成 */
export type TodoStatus = 'P' | 'D'

export interface TodoItem {
  id: number
  title: string
  content?: string | null
  /** 计划日期，yyyy-MM-dd */
  planDate?: string
  priority: TodoPriority
  /** 后端翻译好的优先级文案 */
  priorityText: string
  status: TodoStatus
  statusText: string
  /** 是否已完成，后端算好的 */
  done: boolean
  /** 是否逾期（计划日期早于今天且未完成） */
  overdue: boolean
  finishTime?: string | null
  sortNo?: number
  createTime?: string
}

/** 工作台首屏的今日汇总 */
export interface TodayTodo {
  date: string
  weekdayText: string
  total: number
  done: number
  pending: number
  overdue: number
  /** 0~100 */
  rate: number
  items: TodoItem[]
}

export interface TodoQueryParams {
  page?: number
  size?: number
  keyword?: string
  status?: string
  priority?: string
  /** yyyy-MM-dd */
  startDate?: string
  /** yyyy-MM-dd */
  endDate?: string
}

export interface TodoSaveParams {
  title: string
  content?: string
  /** 不传默认今天 */
  planDate?: string
  priority?: TodoPriority
  sortNo?: number
}

/** 今日待办 + 统计，工作台首屏只发这一个请求 */
export const getTodayTodosApi = () => request<TodayTodo>({ url: '/todo/today', method: 'get' })

/** 历史待办分页 */
export const pageTodosApi = (params: TodoQueryParams) =>
  request<PageResult<TodoItem>>({ url: '/todo/page', method: 'get', params })

export const getTodoApi = (id: number) => request<TodoItem>({ url: `/todo/${id}`, method: 'get' })

export const createTodoApi = (data: TodoSaveParams) =>
  request<number>({ url: '/todo', method: 'post', data })

export const updateTodoApi = (id: number, data: TodoSaveParams) =>
  request<void>({ url: `/todo/${id}`, method: 'put', data })

/** 勾选 / 取消完成 */
export const updateTodoStatusApi = (id: number, status: TodoStatus) =>
  request<void>({ url: `/todo/${id}/status`, method: 'put', params: { status } })

export const deleteTodoApi = (id: number) => request<void>({ url: `/todo/${id}`, method: 'delete' })
