import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

// 買家通知收件匣（區別於 notification.ts 的「通知模板管理」）
// 對齊後端 NotificationController /v2/notifications 與 NotificationDto

export type NotificationType =
  | 'ORDER_CONFIRMED'
  | 'ORDER_PAID'
  | 'ORDER_SHIPPED'
  | 'ORDER_DELIVERED'
  | 'ORDER_COMPLETED'
  | 'ORDER_CANCELLED'
  | 'BOOKING_CONFIRMED'
  | 'BOOKING_REMINDER'
  | 'PAYMENT_SUCCESS'
  | 'PAYMENT_FAILED'
  | 'REVIEW_REQUEST'
  | 'NEW_MESSAGE'
  | 'SYSTEM_ANNOUNCEMENT'

export interface InboxNotification {
  notificationId: string
  userId: string
  notificationType: string
  title: string
  content: string
  data?: Record<string, unknown>
  channel: string | null
  isRead: boolean
  readAt: string | null
  createdAt: string
}

export interface NotificationListResult {
  notifications: InboxNotification[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  unreadCount: number
}

interface UnreadCountResponse {
  userId: string
  unreadCount: number
}

interface ApiResponse<T> {
  success: boolean
  code?: string
  message?: string
  data: T
}

export interface InboxQuery {
  page?: number
  size?: number
  unreadOnly?: boolean
}

// 通知型別 → 中文標籤（找不到時退回原字串）
export const NOTIFICATION_TYPE_LABELS: Record<string, string> = {
  ORDER_CONFIRMED: '訂單已確認',
  ORDER_PAID: '訂單已付款',
  ORDER_SHIPPED: '訂單已出貨',
  ORDER_DELIVERED: '訂單已送達',
  ORDER_COMPLETED: '訂單已完成',
  ORDER_CANCELLED: '訂單已取消',
  BOOKING_CONFIRMED: '預訂已確認',
  BOOKING_REMINDER: '預訂提醒',
  PAYMENT_SUCCESS: '付款成功',
  PAYMENT_FAILED: '付款失敗',
  REVIEW_REQUEST: '邀請評價',
  NEW_MESSAGE: '新訊息',
  SYSTEM_ANNOUNCEMENT: '系統公告',
}

class NotificationInboxService {
  async getNotifications(query: InboxQuery = {}): Promise<NotificationListResult> {
    const params = new URLSearchParams()
    if (query.page !== undefined) params.append('page', String(query.page))
    if (query.size !== undefined) params.append('size', String(query.size))
    if (query.unreadOnly !== undefined) params.append('unreadOnly', String(query.unreadOnly))
    const qs = params.toString()
    const response = await apiClient.get<ApiResponse<NotificationListResult>>(
      API_ENDPOINTS.notifications.list + (qs ? '?' + qs : '')
    )
    return response.data.data
  }

  async getUnreadCount(): Promise<number> {
    const response = await apiClient.get<ApiResponse<UnreadCountResponse>>(
      API_ENDPOINTS.notifications.unreadCount
    )
    return response.data.data.unreadCount
  }

  // notificationIds 省略或傳 null → 後端標記全部已讀
  async markAsRead(notificationIds?: string[]): Promise<NotificationListResult> {
    const response = await apiClient.put<ApiResponse<NotificationListResult>>(
      API_ENDPOINTS.notifications.read,
      { notificationIds: notificationIds ?? null }
    )
    return response.data.data
  }

  async deleteNotification(notificationId: string): Promise<void> {
    await apiClient.delete(API_ENDPOINTS.notifications.delete(notificationId))
  }
}

export default new NotificationInboxService()
