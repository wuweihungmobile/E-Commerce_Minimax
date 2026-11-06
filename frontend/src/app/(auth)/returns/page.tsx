'use client'

import Link from 'next/link'
import ReturnService, {
  RETURN_STATUS_LABELS,
  returnStatusBadgeVariant,
} from '@/services/returns'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { StorefrontShell } from '@/components/layout/StorefrontShell'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { EmptyState } from '@/components/ui/states'
import { useAsyncData } from '@/hooks/useAsyncData'

export default function MyReturnsPage() {
  const { data, error, loading } = useAsyncData(
    () => ReturnService.listMyReturns(),
    [],
    { errorMessage: '無法載入退貨申請列表，請稍後再試' }
  )
  const returns = data?.content ?? []

  return (
    <StorefrontShell>
      <div className="mb-6">
        <PageTitle>我的退貨申請</PageTitle>
        <PageDescription>
          {loading ? '載入中…' : `共 ${returns.length} 筆申請`}
        </PageDescription>
      </div>

      {error && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {loading ? (
        <div className="space-y-4">
          {[0, 1].map((i) => (
            <Card key={i}>
              <CardContent className="py-6">
                <Skeleton className="h-5 w-40 mb-3" />
                <Skeleton className="h-4 w-24" />
              </CardContent>
            </Card>
          ))}
        </div>
      ) : returns.length === 0 && !error ? (
        <Card>
          <EmptyState icon="📦" title="尚無退貨申請" description="可於「我的訂單」中，對已送達的訂單提出退貨申請" action={<Link href="/orders">
              <Button variant="outline">前往我的訂單</Button>
            </Link>} className="py-16" />
        </Card>
      ) : (
        <div className="space-y-4">
          {returns.map((item) => (
            <Link key={item.id} href={`/returns/${item.id}`}>
              <Card className="hover:border-rs-primary transition-colors">
                <CardContent className="py-5">
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-sm font-medium text-rs-ink">{item.returnNumber}</span>
                        <Badge variant={returnStatusBadgeVariant(item.status)}>
                          {RETURN_STATUS_LABELS[item.status]}
                        </Badge>
                      </div>
                      <p className="text-sm text-rs-ink-muted">
                        {item.items.length} 項商品 ·{' '}
                        {new Date(item.createdAt).toLocaleDateString('zh-TW')}
                      </p>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </StorefrontShell>
  )
}
