'use client'

import { useState, useEffect, useCallback } from 'react'
import Link from 'next/link'
import AuthService from '@/services/auth'
import NotificationInboxService, {
  type InboxNotification,
  NOTIFICATION_TYPE_LABELS,
} from '@/services/notificationInbox'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'

const PAGE_SIZE = 15

function typeLabel(type: string) {
  return NOTIFICATION_TYPE_LABELS[type] ?? type
}

function formatDateTime(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-TW', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function orderIdOf(n: InboxNotification): string | null {
  const id = n.data?.orderId
  return typeof id === 'string' ? id : null
}

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState<InboxNotification[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [unreadCount, setUnreadCount] = useState(0)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(
    async (signal?: { cancelled: boolean }) => {
      setLoading(true)
      setError(null)
      try {
        const result = await NotificationInboxService.getNotifications({
          page,
          size: PAGE_SIZE,
          unreadOnly,
        })
        if (signal?.cancelled) return
        setNotifications(result.notifications)
        setTotalPages(result.totalPages)
        setUnreadCount(result.unreadCount)
      } catch {
        if (signal?.cancelled) return
        setError('無法載入通知，請稍後再試')
      } finally {
        if (!signal?.cancelled) setLoading(false)
      }
    },
    [page, unreadOnly]
  )

  useEffect(() => {
    const signal = { cancelled: false }
    load(signal)
    return () => {
      signal.cancelled = true
    }
  }, [load])

  const handleMarkRead = async (id: string) => {
    setBusy(true)
    try {
      await NotificationInboxService.markAsRead([id])
      await load()
    } catch {
      setError('標記已讀失敗，請稍後再試')
    } finally {
      setBusy(false)
    }
  }

  const handleMarkAllRead = async () => {
    setBusy(true)
    try {
      await NotificationInboxService.markAsRead()
      await load()
    } catch {
      setError('標記全部已讀失敗，請稍後再試')
    } finally {
      setBusy(false)
    }
  }

  const handleDelete = async (id: string) => {
    setBusy(true)
    try {
      await NotificationInboxService.deleteNotification(id)
      await load()
    } catch {
      setError('刪除通知失敗，請稍後再試')
    } finally {
      setBusy(false)
    }
  }

  const changeFilter = (next: boolean) => {
    if (next === unreadOnly) return
    setPage(0)
    setUnreadOnly(next)
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16 items-center">
            <div className="flex items-center gap-4">
              <Link href="/" className="text-xl font-bold text-gray-900">
                NextKey
              </Link>
              <span className="text-gray-400">/</span>
              <span className="text-gray-900 font-medium">通知</span>
            </div>
            <div className="flex items-center">
              <span className="text-sm text-gray-600">
                {AuthService.getCurrentUser()?.email}
              </span>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-3xl mx-auto py-6 px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">通知收件匣</h1>
            <p className="mt-1 text-sm text-gray-600">
              {unreadCount > 0 ? `${unreadCount} 則未讀` : '沒有未讀通知'}
            </p>
          </div>
          <Button variant="outline" onClick={handleMarkAllRead} disabled={busy || unreadCount === 0}>
            全部標為已讀
          </Button>
        </div>

        {/* Filter */}
        <div className="flex gap-2 mb-4">
          <Button
            size="sm"
            variant={unreadOnly ? 'outline' : 'default'}
            onClick={() => changeFilter(false)}
          >
            全部
          </Button>
          <Button
            size="sm"
            variant={unreadOnly ? 'default' : 'outline'}
            onClick={() => changeFilter(true)}
          >
            僅未讀
          </Button>
        </div>

        {error && (
          <Alert variant="destructive" className="mb-4">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {loading ? (
          <div className="space-y-3">
            {[0, 1, 2, 3].map((i) => (
              <Card key={i}>
                <CardContent className="py-4">
                  <Skeleton className="h-4 w-32 mb-2" />
                  <Skeleton className="h-4 w-full" />
                </CardContent>
              </Card>
            ))}
          </div>
        ) : notifications.length === 0 && !error ? (
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-16">
              <div className="text-5xl mb-4">🔔</div>
              <h2 className="text-lg font-semibold text-gray-900 mb-1">
                {unreadOnly ? '沒有未讀通知' : '尚無通知'}
              </h2>
              <p className="text-sm text-gray-500">訂單、付款與物流的更新會顯示在這裡</p>
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-3">
            {notifications.map((n) => {
              const orderId = orderIdOf(n)
              return (
                <Card key={n.notificationId} className={n.isRead ? '' : 'border-l-4 border-l-primary'}>
                  <CardContent className="py-4">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          <Badge variant={n.isRead ? 'secondary' : 'default'}>
                            {typeLabel(n.notificationType)}
                          </Badge>
                          {!n.isRead && <span className="text-xs text-primary font-medium">未讀</span>}
                        </div>
                        <p className="text-sm font-medium text-gray-900">{n.title}</p>
                        <p className="text-sm text-gray-600 mt-0.5 whitespace-pre-line">{n.content}</p>
                        <p className="text-xs text-gray-400 mt-1">{formatDateTime(n.createdAt)}</p>
                        {orderId && (
                          <Link
                            href={`/orders/${orderId}`}
                            className="text-xs text-primary hover:underline mt-1 inline-block"
                          >
                            查看訂單 →
                          </Link>
                        )}
                      </div>
                      <div className="flex flex-col gap-2 shrink-0">
                        {!n.isRead && (
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => handleMarkRead(n.notificationId)}
                            disabled={busy}
                          >
                            已讀
                          </Button>
                        )}
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => handleDelete(n.notificationId)}
                          disabled={busy}
                        >
                          刪除
                        </Button>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              )
            })}
          </div>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-4 mt-6">
            <Button
              variant="outline"
              disabled={page <= 0 || loading}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              上一頁
            </Button>
            <span className="text-sm text-gray-600">
              第 {page + 1} / {totalPages} 頁
            </span>
            <Button
              variant="outline"
              disabled={page >= totalPages - 1 || loading}
              onClick={() => setPage((p) => p + 1)}
            >
              下一頁
            </Button>
          </div>
        )}
      </main>
    </div>
  )
}
