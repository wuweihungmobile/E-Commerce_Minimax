'use client'

import { useState, useEffect, useCallback } from 'react'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import SupportService, { SupportTicket } from '@/services/support'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { EmptyState, LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'

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
      <LoadingState />
    )
  }

  if (error) {
    return (
      <div className="space-y-4">
        <Alert variant="destructive">{error}</Alert>
        <Button variant="outline" onClick={fetchTickets}>重試</Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <PageTitle>平台客服工單</PageTitle>
          <PageDescription>共 {tickets.length} 筆工單（跨租戶）</PageDescription>
        </div>
        <Button variant="outline" onClick={fetchTickets}>重新整理</Button>
      </div>

      {tickets.length === 0 ? (
        <Card>
          <EmptyState title="尚無客服工單" className="h-48" />
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
