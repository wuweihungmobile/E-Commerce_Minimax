'use client'

import { useState } from 'react'
import Link from 'next/link'
import NotificationInboxService, {
  type InboxNotification,
  NOTIFICATION_TYPE_LABELS,
} from '@/services/notificationInbox'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { StorefrontShell } from '@/components/layout/StorefrontShell'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { EmptyState } from '@/components/ui/states'
import { useAsyncData } from '@/hooks/useAsyncData'

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

// 通知的 data 帶著對象的 id（訂單、訂房、房源）；不是字串就當作沒有
function dataIdOf(n: InboxNotification, key: 'orderId' | 'bookingId' | 'listingId'): string | null {
  const id = n.data?.[key]
  return typeof id === 'string' ? id : null
}

export default function NotificationsPage() {
  const [page, setPage] = useState(0)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const { data, error: loadError, loading, reload } = useAsyncData(
    () =>
      NotificationInboxService.getNotifications({
        page,
        size: PAGE_SIZE,
        unreadOnly,
      }),
    [page, unreadOnly],
    { errorMessage: () => '無法載入通知，請稍後再試' }
  )
  const notifications = data?.notifications ?? []
  const totalPages = data?.totalPages ?? 0
  const unreadCount = data?.unreadCount ?? 0
  // 動作（標記已讀、刪除）失敗的訊息與載入失敗共用同一個顯示位置；任何新的載入（換頁、換篩選、動作後重抓）都會清掉它
  const [actionError, setActionError] = useState<string | null>(null)
  const error = actionError ?? loadError
  const [busy, setBusy] = useState(false)

  const handleMarkRead = async (id: string) => {
    setBusy(true)
    try {
      await NotificationInboxService.markAsRead([id])
      setActionError(null)
      reload()
    } catch {
      setActionError('標記已讀失敗，請稍後再試')
    } finally {
      setBusy(false)
    }
  }

  const handleMarkAllRead = async () => {
    setBusy(true)
    try {
      await NotificationInboxService.markAsRead()
      setActionError(null)
      reload()
    } catch {
      setActionError('標記全部已讀失敗，請稍後再試')
    } finally {
      setBusy(false)
    }
  }

  const handleDelete = async (id: string) => {
    setBusy(true)
    try {
      await NotificationInboxService.deleteNotification(id)
      setActionError(null)
      reload()
    } catch {
      setActionError('刪除通知失敗，請稍後再試')
    } finally {
      setBusy(false)
    }
  }

  const changeFilter = (next: boolean) => {
    if (next === unreadOnly) return
    setActionError(null)
    setPage(0)
    setUnreadOnly(next)
  }

  return (
    <StorefrontShell>
        <div className="flex items-center justify-between mb-6">
          <div>
            <PageTitle>通知收件匣</PageTitle>
            <PageDescription>
              {unreadCount > 0 ? `${unreadCount} 則未讀` : '沒有未讀通知'}
            </PageDescription>
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
            <EmptyState icon="🔔" title={unreadOnly ? '沒有未讀通知' : '尚無通知'} description="訂單、付款與物流的更新會顯示在這裡" className="py-16" />
          </Card>
        ) : (
          <div className="space-y-3">
            {notifications.map((n) => {
              const orderId = dataIdOf(n, 'orderId')
              const bookingId = dataIdOf(n, 'bookingId')
              const listingId = dataIdOf(n, 'listingId')
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
                        <p className="text-sm font-medium text-rs-ink">{n.title}</p>
                        <p className="text-sm text-rs-ink-muted mt-0.5 whitespace-pre-line">{n.content}</p>
                        <p className="text-xs text-rs-disabled-text mt-1">{formatDateTime(n.createdAt)}</p>
                        {orderId && (
                          <Link
                            href={`/orders/${orderId}`}
                            className="text-xs text-primary hover:underline mt-1 mr-3 inline-block"
                          >
                            查看訂單 →
                          </Link>
                        )}
                        {bookingId && (
                          <Link
                            href={`/bookings/${bookingId}`}
                            className="text-xs text-primary hover:underline mt-1 mr-3 inline-block"
                          >
                            查看訂房 →
                          </Link>
                        )}
                        {listingId && (
                          <Link
                            href={`/listings/${listingId}`}
                            prefetch={false}
                            className="text-xs text-primary hover:underline mt-1 inline-block"
                          >
                            重新預訂 →
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
              onClick={() => {
                setActionError(null)
                setPage((p) => Math.max(0, p - 1))
              }}
            >
              上一頁
            </Button>
            <span className="text-sm text-rs-ink-muted">
              第 {page + 1} / {totalPages} 頁
            </span>
            <Button
              variant="outline"
              disabled={page >= totalPages - 1 || loading}
              onClick={() => {
                setActionError(null)
                setPage((p) => p + 1)
              }}
            >
              下一頁
            </Button>
          </div>
        )}
    </StorefrontShell>
  )
}
