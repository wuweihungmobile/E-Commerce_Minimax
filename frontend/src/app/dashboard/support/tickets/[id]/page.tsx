'use client'

import { useState } from 'react'
import { useParams } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import SupportService, { TicketStatus } from '@/services/support'
import { useAsyncData } from '@/hooks/useAsyncData'
import { Textarea } from '@/components/ui/textarea'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'
import { toast } from '@/components/ui/toast'
import { apiErrorMessage } from '@/lib/api-error'

const STATUS_LABEL: Record<TicketStatus, string> = {
  OPEN: '開立',
  IN_PROGRESS: '處理中',
  RESOLVED: '已解決',
  CLOSED: '已關閉',
}

const NEXT_STATUSES: Record<TicketStatus, TicketStatus[]> = {
  OPEN: ['IN_PROGRESS', 'CLOSED'],
  IN_PROGRESS: ['RESOLVED', 'CLOSED'],
  RESOLVED: ['CLOSED'],
  CLOSED: [],
}

export default function DashboardSupportTicketDetailPage() {
  const params = useParams()
  const ticketId = params.id as string

  const { data: ticket, error, loading, reload } = useAsyncData(
    () => SupportService.getTenantTicket(ticketId),
    [ticketId],
    { errorMessage: '載入工單詳情失敗' }
  )
  const [reply, setReply] = useState('')
  const [actionLoading, setActionLoading] = useState(false)

  const handleStatusChange = async (status: TicketStatus) => {
    setActionLoading(true)
    try {
      await SupportService.updateTicketStatus(ticketId, status)
      reload()
    } catch (err: unknown) {
      toast.error(apiErrorMessage(err, '更新狀態失敗'))
    } finally {
      setActionLoading(false)
    }
  }

  const handleReply = async () => {
    if (!reply.trim()) return
    setActionLoading(true)
    try {
      await SupportService.postStaffMessage(ticketId, reply.trim())
      setReply('')
      reload()
    } catch (err: unknown) {
      toast.error(apiErrorMessage(err, '發送訊息失敗'))
    } finally {
      setActionLoading(false)
    }
  }

  if (loading) {
    return (
      <LoadingState />
    )
  }

  if (error || !ticket) {
    return (
      <div className="space-y-4">
        <PageTitle className="sr-only">工單詳情</PageTitle>
        <Alert variant="destructive">
          {error || '找不到工單'}
        </Alert>
        <Link href="/dashboard/support/tickets">
          <Button variant="outline">返回列表</Button>
        </Link>
      </div>
    )
  }

  const nextStatuses = NEXT_STATUSES[ticket.status]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <PageTitle>{ticket.subject}</PageTitle>
          <PageDescription>{ticket.ticketNumber}</PageDescription>
        </div>
        <Badge variant={ticket.status === 'CLOSED' ? 'outline' : 'default'}>
          {STATUS_LABEL[ticket.status]}
        </Badge>
      </div>

      <Card>
        <CardContent className="py-5">
          <p className="text-sm text-rs-ink whitespace-pre-wrap">{ticket.description}</p>
        </CardContent>
        {nextStatuses.length > 0 && (
          <CardFooter className="flex gap-2">
            {nextStatuses.map((status) => (
              <Button
                key={status}
                variant="outline"
                size="sm"
                onClick={() => handleStatusChange(status)}
                disabled={actionLoading}
              >
                轉為「{STATUS_LABEL[status]}」
              </Button>
            ))}
          </CardFooter>
        )}
      </Card>

      <h2 className="text-sm font-semibold">對話記錄</h2>
      <div className="space-y-3">
        {(ticket.messages || []).length === 0 ? (
          <p className="text-sm text-rs-ink-muted">尚無訊息</p>
        ) : (
          ticket.messages!.map((message) => (
            <Card key={message.id} className={message.senderType === 'STAFF' ? 'bg-rs-bg-base' : ''}>
              <CardContent className="py-3">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-xs font-medium">
                    {message.senderType === 'STAFF' ? '客服' : '買家'}
                  </span>
                  <span className="text-xs text-rs-disabled-text">
                    {new Date(message.createdAt).toLocaleString('zh-TW')}
                  </span>
                </div>
                <p className="text-sm text-rs-ink whitespace-pre-wrap">{message.message}</p>
              </CardContent>
            </Card>
          ))
        )}
      </div>

      {ticket.status !== 'CLOSED' && (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">回覆買家</CardTitle>
          </CardHeader>
          <CardContent>
            <Textarea
              placeholder="輸入回覆內容..."
              value={reply}
              onChange={(e) => setReply(e.target.value)}
            />
            <div className="flex justify-end mt-2">
              <Button onClick={handleReply} disabled={actionLoading || !reply.trim()}>
                發送
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
