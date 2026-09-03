'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import ReturnService, {
  type ReturnRequest,
  RETURN_STATUS_LABELS,
  returnStatusBadgeVariant,
} from '@/services/returns'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosErr = error as { response?: { data?: { message?: string } } }
    return axiosErr.response?.data?.message || fallback
  }
  return fallback
}

export default function MyReturnsPage() {
  const [returns, setReturns] = useState<ReturnRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const result = await ReturnService.listMyReturns()
        if (cancelled) return
        setReturns(result.content)
      } catch (err) {
        if (cancelled) return
        setError(extractErrorMessage(err, '無法載入退貨申請列表，請稍後再試'))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <StorefrontShell>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">我的退貨申請</h1>
        <p className="mt-1 text-sm text-gray-600">
          {loading ? '載入中…' : `共 ${returns.length} 筆申請`}
        </p>
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
          <CardContent className="flex flex-col items-center justify-center py-16">
            <div className="text-5xl mb-4">📦</div>
            <h2 className="text-lg font-semibold text-gray-900 mb-1">尚無退貨申請</h2>
            <p className="text-sm text-gray-500 mb-6">
              可於「我的訂單」中，對已送達的訂單提出退貨申請
            </p>
            <Link href="/orders">
              <Button variant="outline">前往我的訂單</Button>
            </Link>
          </CardContent>
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
                        <span className="text-sm font-medium text-gray-900">{item.returnNumber}</span>
                        <Badge variant={returnStatusBadgeVariant(item.status)}>
                          {RETURN_STATUS_LABELS[item.status]}
                        </Badge>
                      </div>
                      <p className="text-sm text-gray-500">
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
