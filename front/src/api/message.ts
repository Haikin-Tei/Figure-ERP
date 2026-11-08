import request from './request'
import type { PageResult } from './warehouse'

/** 消息类型：审批 / 订单 / 库存 / 系统 */
export type MessageType = 'APPROVAL' | 'ORDER' | 'INVENTORY' | 'SYSTEM'

/** 消息状态：未读 / 已读 / 已处理 */
export type MessageStatus = 'U' | 'R' | 'H'

/** 优先级：普通 / 紧急 */
export type MessagePriority = 'N' | 'U'

export interface MessageItem {
  id: number
  title: string
  content?: string | null
  msgType: MessageType
  /** 后端翻译好的类型文案 */
  msgTypeText: string
  bizType?: string | null
  bizId?: number | null
  /** 「去处理」跳转的前端路由，为空表示纯通知 */
  bizUrl?: string | null
  senderName?: string | null
  priority: MessagePriority
  priorityText: string
  status: MessageStatus
  statusText: string
  unread: boolean
  handled: boolean
  urgent: boolean
  readTime?: string | null
  handleTime?: string | null
  handleRemark?: string | null
  createTime?: string
}

export interface MessageTypeCount {
  type: MessageType
  typeText: string
  total: number
  unread: number
}

export interface MessageSummary {
  total: number
  unread: number
  urgentUnread: number
  handled: number
  byType: MessageTypeCount[]
}

export interface MessageQueryParams {
  page?: number
  size?: number
  msgType?: string
  status?: string
  priority?: string
  keyword?: string
}

export interface MessageSendParams {
  receiverId: number
  title: string
  content?: string
  msgType?: string
  bizType?: string
  bizId?: number
  bizUrl?: string
  priority?: string
  remark?: string
}

/** 分页查询我的消息 */
export const pageMessagesApi = (params: MessageQueryParams) =>
  request<PageResult<MessageItem>>({ url: '/message/page', method: 'get', params })

/** 未读统计（右上角红点 + 抽屉顶部小结） */
export const getMessageSummaryApi = () =>
  request<MessageSummary>({ url: '/message/summary', method: 'get' })

/** 消息类型字典 */
export const getMessageTypesApi = () =>
  request<Array<{ code: string; text: string }>>({ url: '/message/types', method: 'get' })

/** 详情：未读消息查看后自动变为已读 */
export const getMessageApi = (id: number) =>
  request<MessageItem>({ url: `/message/${id}`, method: 'get' })

export const markMessageReadApi = (id: number) =>
  request<void>({ url: `/message/${id}/read`, method: 'put' })

/** 全部标为已读，可只处理某一类型；返回实际更新条数 */
export const markAllMessagesReadApi = (msgType?: string) =>
  request<number>({ url: '/message/read-all', method: 'put', params: { msgType } })

/** 处理消息，remark 为处理备注（可空） */
export const handleMessageApi = (id: number, remark?: string) =>
  request<void>({ url: `/message/${id}/handle`, method: 'put', data: { remark } })

export const deleteMessageApi = (id: number) =>
  request<void>({ url: `/message/${id}`, method: 'delete' })

export const sendMessageApi = (data: MessageSendParams) =>
  request<number>({ url: '/message', method: 'post', data })
