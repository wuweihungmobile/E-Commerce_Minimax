'use client'

import { useState, useEffect, useCallback } from 'react'
import { useParams, useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import SupportService, { SupportTicket, TicketStatus } from '@/services/support'
import AuthService from '@/services/auth'

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
  const router = useRouter()
  const ticketId = params.id as string

  const [ticket, setTicket] = useState<SupportTicket | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reply, setReply] = useState('')
  const [actionLoading, setActionLoading] = useState(false)

  const fetchTicket = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await SupportService.getTenantTicket(ticketId)
      setTicket(data)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入工單詳情失敗')
      } else {
        setError('載入工單詳情失敗')
      }
    } finally {
      setLoading(false)
    }
  }, [ticketId])

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
    fetchTicket()
  }, [router, fetchTicket])

  const handleStatusChange = async (status: TicketStatus) => {
    setActionLoading(true)
    try {
      await SupportService.updateTicketStatus(ticketId, status)
      await fetchTicket()
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        alert(axiosErr.response?.data?.message || '更新狀態失敗')
      } else {
        alert('更新狀態失敗')
      }
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
      await fetchTicket()
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        alert(axiosErr.response?.data?.message || '發送訊息失敗')
      } else {
        alert('發送訊息失敗')
      }
    } finally {
      setActionLoading(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">載入中...</div>
      </div>
    )
  }

  if (error || !ticket) {
    return (
      <div className="space-y-4">
        <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">
          {error || '找不到工單'}
        </div>
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
          <h1 className="text-2xl font-bold">{ticket.subject}</h1>
          <p className="text-muted-foreground">{ticket.ticketNumber}</p>
        </div>
        <Badge variant={ticket.status === 'CLOSED' ? 'outline' : 'default'}>
          {STATUS_LABEL[ticket.status]}
        </Badge>
      </div>

      <Card>
        <CardContent className="py-5">
          <p className="text-sm text-gray-700 whitespace-pre-wrap">{ticket.description}</p>
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
          <p className="text-sm text-gray-500">尚無訊息</p>
        ) : (
          ticket.messages!.map((message) => (
            <Card key={message.id} className={message.senderType === 'STAFF' ? 'bg-gray-50' : ''}>
              <CardContent className="py-3">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-xs font-medium">
                    {message.senderType === 'STAFF' ? '客服' : '買家'}
                  </span>
                  <span className="text-xs text-gray-400">
                    {new Date(message.createdAt).toLocaleString('zh-TW')}
                  </span>
                </div>
                <p className="text-sm text-gray-700 whitespace-pre-wrap">{message.message}</p>
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
            <textarea
              className="w-full border rounded-md px-3 py-2 text-sm min-h-20"
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
