'use client'

import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import SupportService, { SupportTicket } from '@/services/support'
import { useAsyncData } from '@/hooks/useAsyncData'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { EmptyState, LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'

const STATUS_LABEL: Record<SupportTicket['status'], string> = {
  OPEN: '開立',
  IN_PROGRESS: '處理中',
  RESOLVED: '已解決',
  CLOSED: '已關閉',
}

export default function DashboardSupportTicketsPage() {
  const { data, error, loading, reload } = useAsyncData(
    () => SupportService.listTenantTickets(),
    [],
    { errorMessage: '載入工單列表失敗' }
  )
  const tickets = data?.tickets ?? []

  if (loading) {
    return (
      <LoadingState />
    )
  }

  if (error) {
    return (
      <div className="space-y-4">
        <Alert variant="destructive">{error}</Alert>
        <Button variant="outline" onClick={reload}>重試</Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <PageTitle>客服工單</PageTitle>
          <PageDescription>共 {tickets.length} 筆工單</PageDescription>
        </div>
        <Button variant="outline" onClick={reload}>重新整理</Button>
      </div>

      {tickets.length === 0 ? (
        <Card>
          <EmptyState title="尚無客服工單" className="h-48" />
        </Card>
      ) : (
        <div className="space-y-4">
          {tickets.map((ticket) => (
            <Link key={ticket.id} href={`/dashboard/support/tickets/${ticket.id}`}>
              <Card className="hover:border-primary transition-colors">
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle className="text-lg">{ticket.subject}</CardTitle>
                      <CardDescription>{ticket.ticketNumber}</CardDescription>
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
