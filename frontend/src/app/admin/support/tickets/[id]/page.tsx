'use client'

import { useState, useEffect, useCallback } from 'react'
import { useParams } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import SupportService, { SupportTicket } from '@/services/support'

const STATUS_LABEL: Record<SupportTicket['status'], string> = {
  OPEN: '開立',
  IN_PROGRESS: '處理中',
  RESOLVED: '已解決',
  CLOSED: '已關閉',
}

export default function AdminSupportTicketDetailPage() {
  const params = useParams()
  const ticketId = params.id as string

  const [ticket, setTicket] = useState<SupportTicket | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reply, setReply] = useState('')
  const [assignedTo, setAssignedTo] = useState('')
  const [actionLoading, setActionLoading] = useState(false)

  const fetchTicket = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await SupportService.getAdminTicket(ticketId)
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
    fetchTicket()
  }, [fetchTicket])

  const handleAssign = async () => {
    if (!assignedTo.trim()) return
    setActionLoading(true)
    try {
      await SupportService.assignTicket(ticketId, assignedTo.trim())
      setAssignedTo('')
      await fetchTicket()
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        alert(axiosErr.response?.data?.message || '指派失敗')
      } else {
        alert('指派失敗')
      }
    } finally {
      setActionLoading(false)
    }
  }

  const handleReply = async () => {
    if (!reply.trim()) return
    setActionLoading(true)
    try {
      // 平台角色的回覆走與店家共用的 dashboard 端點，後端依角色自動判斷是否略過租戶篩選
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
        <Link href="/admin/support/tickets">
          <Button variant="outline">返回列表</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">{ticket.subject}</h1>
          <p className="text-muted-foreground">
            {ticket.ticketNumber} · {ticket.tenantId ? `租戶 ${ticket.tenantId.slice(0, 8)}` : '平台工單'}
          </p>
        </div>
        <Badge variant={ticket.status === 'CLOSED' ? 'outline' : 'default'}>
          {STATUS_LABEL[ticket.status]}
        </Badge>
      </div>

      <Card>
        <CardContent className="py-5 space-y-2">
          <p className="text-sm text-gray-700 whitespace-pre-wrap">{ticket.description}</p>
          <p className="text-xs text-gray-500">
            目前處理人：{ticket.assignedTo || '尚未指派'}
          </p>
        </CardContent>
        <CardFooter className="flex flex-col gap-2 items-stretch">
          <Label htmlFor="assignedTo">指派處理人（使用者 ID）</Label>
          <div className="flex gap-2">
            <Input
              id="assignedTo"
              value={assignedTo}
              onChange={(e) => setAssignedTo(e.target.value)}
              placeholder="輸入使用者 UUID"
            />
            <Button onClick={handleAssign} disabled={actionLoading || !assignedTo.trim()}>
              指派
            </Button>
          </div>
        </CardFooter>
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
            <CardTitle className="text-sm">回覆</CardTitle>
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
