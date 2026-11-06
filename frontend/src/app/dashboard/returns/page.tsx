'use client'

import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import ReturnService, {
  RETURN_STATUS_LABELS,
  returnStatusBadgeVariant,
} from '@/services/returns'
import { useAsyncData } from '@/hooks/useAsyncData'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { EmptyState, LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'

export default function DashboardReturnsPage() {
  const { data, error, loading, reload } = useAsyncData(
    () => ReturnService.listTenantReturns(),
    [],
    { errorMessage: '載入退貨申請列表失敗' }
  )
  const returns = data?.content ?? []

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
          <PageTitle>退貨審核</PageTitle>
          <PageDescription>共 {returns.length} 筆退貨申請</PageDescription>
        </div>
        <Button variant="outline" onClick={reload}>重新整理</Button>
      </div>

      {returns.length === 0 ? (
        <Card>
          <EmptyState title="尚無退貨申請" className="h-48" />
        </Card>
      ) : (
        <div className="space-y-4">
          {returns.map((item) => (
            <Link key={item.id} href={`/dashboard/returns/${item.id}`}>
              <Card className="hover:border-primary transition-colors">
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle className="text-lg">{item.returnNumber}</CardTitle>
                      <CardDescription>
                        {item.items.length} 項商品 · {new Date(item.createdAt).toLocaleDateString('zh-TW')}
                      </CardDescription>
                    </div>
                    <Badge variant={returnStatusBadgeVariant(item.status)}>
                      {RETURN_STATUS_LABELS[item.status]}
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
