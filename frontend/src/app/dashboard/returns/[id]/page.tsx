'use client'

import { useState, useEffect, useCallback } from 'react'
import { useParams, useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import ReturnService, {
  type ReturnRequest,
  RETURN_STATUS_LABELS,
  returnStatusBadgeVariant,
} from '@/services/returns'
import AuthService from '@/services/auth'

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosErr = error as { response?: { data?: { message?: string } } }
    return axiosErr.response?.data?.message || fallback
  }
  return fallback
}

/** 收貨確認每一項的暫存輸入：預設可售數量＝申請數量，不可售為 0。 */
interface ReceiveDraft {
  sellableQty: number
  unsellableQty: number
}

export default function DashboardReturnDetailPage() {
  const params = useParams()
  const router = useRouter()
  const returnId = params.id as string

  const [returnRequest, setReturnRequest] = useState<ReturnRequest | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const [showRejectForm, setShowRejectForm] = useState(false)
  const [rejectionReason, setRejectionReason] = useState('')

  const [receiveDrafts, setReceiveDrafts] = useState<Record<string, ReceiveDraft>>({})

  const fetchReturn = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await ReturnService.getTenantReturn(returnId)
      setReturnRequest(data)
      setReceiveDrafts(
        Object.fromEntries(
          data.items.map((item) => [item.id, { sellableQty: item.requestedQty, unsellableQty: 0 }])
        )
      )
    } catch (err) {
      setError(extractErrorMessage(err, '載入退貨申請詳情失敗'))
    } finally {
      setLoading(false)
    }
  }, [returnId])

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
    fetchReturn()
  }, [router, fetchReturn])

  async function handleApprove() {
    setActionLoading(true)
    setActionError(null)
    try {
      await ReturnService.approveReturn(returnId)
      await fetchReturn()
    } catch (err) {
      setActionError(extractErrorMessage(err, '核准失敗'))
    } finally {
      setActionLoading(false)
    }
  }

  async function handleReject() {
    setActionLoading(true)
    setActionError(null)
    try {
      await ReturnService.rejectReturn(returnId, rejectionReason.trim() || undefined)
      setShowRejectForm(false)
      setRejectionReason('')
      await fetchReturn()
    } catch (err) {
      setActionError(extractErrorMessage(err, '駁回失敗'))
    } finally {
      setActionLoading(false)
    }
  }

  function setDraft(itemId: string, field: keyof ReceiveDraft, max: number, value: number) {
    const clamped = Math.max(0, Math.min(max, Math.floor(value) || 0))
    setReceiveDrafts((prev) => ({ ...prev, [itemId]: { ...prev[itemId], [field]: clamped } }))
  }

  async function handleReceive() {
    if (!returnRequest) return
    for (const item of returnRequest.items) {
      const draft = receiveDrafts[item.id]
      if (!draft || draft.sellableQty + draft.unsellableQty > item.requestedQty) {
        setActionError('可售與不可售數量加總不得超過申請數量')
        return
      }
    }
    setActionLoading(true)
    setActionError(null)
    try {
      await ReturnService.receiveReturn(
        returnId,
        returnRequest.items.map((item) => ({
          itemId: item.id,
          sellableQty: receiveDrafts[item.id]?.sellableQty ?? 0,
          unsellableQty: receiveDrafts[item.id]?.unsellableQty ?? 0,
        }))
      )
      await fetchReturn()
    } catch (err) {
      setActionError(extractErrorMessage(err, '收貨確認失敗'))
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

  if (error || !returnRequest) {
    return (
      <div className="space-y-4">
        <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">
          {error || '找不到退貨申請'}
        </div>
        <Link href="/dashboard/returns">
          <Button variant="outline">返回列表</Button>
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">{returnRequest.returnNumber}</h1>
          <p className="text-muted-foreground">
            訂單 #{returnRequest.orderId.slice(0, 8)} ·{' '}
            {new Date(returnRequest.createdAt).toLocaleString('zh-TW')}
          </p>
        </div>
        <Badge variant={returnStatusBadgeVariant(returnRequest.status)}>
          {RETURN_STATUS_LABELS[returnRequest.status]}
        </Badge>
      </div>

      {actionError && (
        <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">
          {actionError}
        </div>
      )}

      {returnRequest.reason && (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">買家退貨原因</CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-gray-700 whitespace-pre-wrap">
            {returnRequest.reason}
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-sm">退貨品項（{returnRequest.items.length}）</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {returnRequest.items.map((item) => (
            <div key={item.id} className="flex items-center gap-4 border-b last:border-b-0 pb-4 last:pb-0">
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-gray-900">品項 #{item.orderItemId.slice(0, 8)}</p>
                <p className="text-xs text-gray-500">申請退貨 {item.requestedQty} 件</p>
              </div>
              {returnRequest.status === 'APPROVED' ? (
                <div className="flex items-center gap-3 shrink-0">
                  <div className="flex items-center gap-1">
                    <Label htmlFor={`sellable-${item.id}`} className="text-xs text-gray-500">
                      可售
                    </Label>
                    <input
                      id={`sellable-${item.id}`}
                      type="number"
                      min={0}
                      max={item.requestedQty}
                      value={receiveDrafts[item.id]?.sellableQty ?? 0}
                      onChange={(e) =>
                        setDraft(item.id, 'sellableQty', item.requestedQty, Number(e.target.value))
                      }
                      className="w-16 border rounded-md px-2 py-1 text-sm text-center"
                    />
                  </div>
                  <div className="flex items-center gap-1">
                    <Label htmlFor={`unsellable-${item.id}`} className="text-xs text-gray-500">
                      不可售
                    </Label>
                    <input
                      id={`unsellable-${item.id}`}
                      type="number"
                      min={0}
                      max={item.requestedQty}
                      value={receiveDrafts[item.id]?.unsellableQty ?? 0}
                      onChange={(e) =>
                        setDraft(item.id, 'unsellableQty', item.requestedQty, Number(e.target.value))
                      }
                      className="w-16 border rounded-md px-2 py-1 text-sm text-center"
                    />
                  </div>
                </div>
              ) : (
                returnRequest.status === 'RECEIVED' && (
                  <div className="text-right text-xs text-gray-600 shrink-0">
                    <p>可售 {item.sellableQty ?? 0}</p>
                    <p>不可售 {item.unsellableQty ?? 0}</p>
                  </div>
                )
              )}
            </div>
          ))}
        </CardContent>

        {returnRequest.status === 'REQUESTED' && !showRejectForm && (
          <CardFooter className="flex gap-2">
            <Button size="sm" onClick={handleApprove} disabled={actionLoading}>
              核准
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setShowRejectForm(true)}
              disabled={actionLoading}
            >
              駁回
            </Button>
          </CardFooter>
        )}

        {returnRequest.status === 'APPROVED' && (
          <CardFooter>
            <Button onClick={handleReceive} disabled={actionLoading}>
              {actionLoading ? '處理中…' : '確認收貨'}
            </Button>
          </CardFooter>
        )}
      </Card>

      {showRejectForm && (
        <Card className="border-red-200">
          <CardHeader>
            <CardTitle className="text-sm">駁回原因（選填）</CardTitle>
          </CardHeader>
          <CardContent>
            <textarea
              className="w-full border rounded-md px-3 py-2 text-sm min-h-20"
              placeholder="請說明駁回原因，買家將可看到此說明"
              value={rejectionReason}
              onChange={(e) => setRejectionReason(e.target.value)}
            />
          </CardContent>
          <CardFooter className="flex gap-2">
            <Button variant="destructive" size="sm" onClick={handleReject} disabled={actionLoading}>
              {actionLoading ? '處理中…' : '確認駁回'}
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setShowRejectForm(false)
                setRejectionReason('')
              }}
              disabled={actionLoading}
            >
              返回
            </Button>
          </CardFooter>
        </Card>
      )}

      {returnRequest.status === 'REJECTED' && returnRequest.rejectionReason && (
        <Card className="border-red-200">
          <CardHeader>
            <CardTitle className="text-sm">駁回原因</CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-gray-700 whitespace-pre-wrap">
            {returnRequest.rejectionReason}
          </CardContent>
        </Card>
      )}
    </div>
  )
}
