'use client'

import { useState, useEffect, useCallback } from 'react'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import SupportService, { SupportTicket } from '@/services/support'

const STATUS_LABEL: Record<SupportTicket['status'], string> = {
  OPEN: '開立',
  IN_PROGRESS: '處理中',
  RESOLVED: '已解決',
  CLOSED: '已關閉',
}

export default function AdminSupportTicketsPage() {
  const [tickets, setTickets] = useState<SupportTicket[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchTickets = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await SupportService.listAllTickets()
      setTickets(data.tickets)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入工單列表失敗')
      } else {
        setError('載入工單列表失敗')
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchTickets()
  }, [fetchTickets])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">載入中...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="space-y-4">
        <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">{error}</div>
        <Button variant="outline" onClick={fetchTickets}>重試</Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">平台客服工單</h1>
          <p className="text-muted-foreground">共 {tickets.length} 筆工單（跨租戶）</p>
        </div>
        <Button variant="outline" onClick={fetchTickets}>重新整理</Button>
      </div>

      {tickets.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-gray-500">尚無客服工單</p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {tickets.map((ticket) => (
            <Link key={ticket.id} href={`/admin/support/tickets/${ticket.id}`}>
              <Card className="hover:border-primary transition-colors">
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle className="text-lg">{ticket.subject}</CardTitle>
                      <CardDescription>
                        {ticket.ticketNumber}
                        {ticket.tenantId
                          ? <span className="font-mono text-xs"> · 租戶 {ticket.tenantId.slice(0, 8)}</span>
                          : <span> · 平台工單</span>}
                      </CardDescription>
                    </div>
                    <Badge variant={ticket.status === 'CLOSED' ? 'outline' : 'default'}>
                      {STATUS_LABEL[ticket.status]}
                    </Badge>
                  </div>
                </CardHeader>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
