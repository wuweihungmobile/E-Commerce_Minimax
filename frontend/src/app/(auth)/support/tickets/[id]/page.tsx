'use client'

import { useEffect, useState } from 'react'
import { useParams } from 'next/navigation'
import Link from 'next/link'
import SupportService, { type SupportTicket } from '@/services/support'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

const STATUS_LABEL: Record<SupportTicket['status'], string> = {
  OPEN: '開立',
  IN_PROGRESS: '處理中',
  RESOLVED: '已解決',
  CLOSED: '已關閉',
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosErr = error as { response?: { data?: { message?: string } } }
    return axiosErr.response?.data?.message || fallback
  }
  return fallback
}

export default function SupportTicketDetailPage() {
  const params = useParams()
  const ticketId = params.id as string

  const [ticket, setTicket] = useState<SupportTicket | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reply, setReply] = useState('')
  const [sending, setSending] = useState(false)
  const [sendError, setSendError] = useState<string | null>(null)

  async function load() {
    setLoading(true)
    setError(null)
    try {
      const result = await SupportService.getTicket(ticketId)
      setTicket(result)
    } catch (err) {
      setError(extractErrorMessage(err, '無法載入工單詳情'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ticketId])

  async function handleReply() {
    if (!reply.trim()) return
    setSending(true)
    setSendError(null)
    try {
      await SupportService.postMessage(ticketId, reply.trim())
      setReply('')
      await load()
    } catch (err) {
      setSendError(extractErrorMessage(err, '發送訊息失敗'))
    } finally {
      setSending(false)
    }
  }

  if (loading) {
    return (
      <StorefrontShell>
        <div className="text-sm text-gray-500">載入中...</div>
      </StorefrontShell>
    )
  }

  if (error || !ticket) {
    return (
      <StorefrontShell>
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{error || '找不到工單'}</AlertDescription>
        </Alert>
        <Link href="/support/tickets">
          <Button variant="outline">返回工單列表</Button>
        </Link>
      </StorefrontShell>
    )
  }

  const isClosed = ticket.status === 'CLOSED'

  return (
    <StorefrontShell>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{ticket.subject}</h1>
          <p className="mt-1 text-sm text-gray-600">{ticket.ticketNumber}</p>
        </div>
        <Badge variant={isClosed ? 'outline' : 'default'}>{STATUS_LABEL[ticket.status]}</Badge>
      </div>

      <Card className="mb-6">
        <CardContent className="py-5">
          <p className="text-sm text-gray-700 whitespace-pre-wrap">{ticket.description}</p>
          <p className="text-xs text-gray-500 mt-3">
            建立時間：{new Date(ticket.createdAt).toLocaleString('zh-TW')}
          </p>
        </CardContent>
      </Card>

      <h2 className="text-sm font-semibold mb-3">對話記錄</h2>
      <div className="space-y-3 mb-6">
        {(ticket.messages || []).length === 0 ? (
          <p className="text-sm text-gray-500">尚無訊息</p>
        ) : (
          ticket.messages!.map((message) => (
            <Card key={message.id} className={message.senderType === 'CUSTOMER' ? '' : 'bg-gray-50'}>
              <CardContent className="py-3">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-xs font-medium text-gray-900">
                    {message.senderType === 'CUSTOMER' ? '我' : '客服'}
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

      {!isClosed && (
        <Card>
          <CardContent className="py-4">
            {sendError && (
              <Alert variant="destructive" className="mb-3">
                <AlertDescription>{sendError}</AlertDescription>
              </Alert>
            )}
            <textarea
              className="w-full border rounded-md px-3 py-2 text-sm min-h-20"
              placeholder="輸入訊息..."
              value={reply}
              onChange={(e) => setReply(e.target.value)}
            />
            <div className="flex justify-end mt-2">
              <Button onClick={handleReply} disabled={sending || !reply.trim()}>
                {sending ? '發送中...' : '發送'}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </StorefrontShell>
  )
}
