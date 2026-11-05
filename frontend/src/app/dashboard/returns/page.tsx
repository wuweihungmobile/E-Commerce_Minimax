'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import ReturnService, {
  type ReturnRequest,
  RETURN_STATUS_LABELS,
  returnStatusBadgeVariant,
} from '@/services/returns'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosErr = error as { response?: { data?: { message?: string } } }
    return axiosErr.response?.data?.message || fallback
  }
  return fallback
}

export default function DashboardReturnsPage() {
  const router = useRouter()
  const [returns, setReturns] = useState<ReturnRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchReturns = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await ReturnService.listTenantReturns()
      setReturns(result.content)
    } catch (err) {
      setError(extractErrorMessage(err, '載入退貨申請列表失敗'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push(loginHref())
      return
    }
    fetchReturns()
  }, [router, fetchReturns])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-rs-ink-muted">載入中...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="space-y-4">
        <div className="bg-rs-error/10 border border-rs-error/30 text-rs-error px-4 py-3 rounded-md">{error}</div>
        <Button variant="outline" onClick={fetchReturns}>重試</Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">退貨審核</h1>
          <p className="text-muted-foreground">共 {returns.length} 筆退貨申請</p>
        </div>
        <Button variant="outline" onClick={fetchReturns}>重新整理</Button>
      </div>

      {returns.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-rs-ink-muted">尚無退貨申請</p>
          </CardContent>
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
