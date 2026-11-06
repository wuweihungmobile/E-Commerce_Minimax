'use client'

import { useState } from 'react'
import { useParams } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import ReturnService, {
  RETURN_STATUS_LABELS,
  returnStatusBadgeVariant,
} from '@/services/returns'
import { useAsyncData } from '@/hooks/useAsyncData'
import { Textarea } from '@/components/ui/textarea'
import { Input } from '@/components/ui/input'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'
import { apiErrorMessage } from '@/lib/api-error'

/** 收貨確認每一項的暫存輸入：預設可售數量＝申請數量，不可售為 0。 */
interface ReceiveDraft {
  sellableQty: number
  unsellableQty: number
}

export default function DashboardReturnDetailPage() {
  const params = useParams()
  const returnId = params.id as string

  const { data: returnRequest, error, loading, reload } = useAsyncData(
    () => ReturnService.getTenantReturn(returnId),
    [returnId],
    { errorMessage: '載入退貨申請詳情失敗' }
  )
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const [showRejectForm, setShowRejectForm] = useState(false)
  const [rejectionReason, setRejectionReason] = useState('')

  // 收貨確認每一項的暫存輸入：只存使用者改過的值，沒改的項目用預設（可售＝申請數量、不可售＝0）。
  // 重新載入（核准、駁回、收貨之後）會清掉——與原本「載入完成就依資料重設全部草稿」的行為相同。
  const [draftOverrides, setDraftOverrides] = useState<Record<string, ReceiveDraft>>({})
  const defaultDraft = (requestedQty: number): ReceiveDraft => ({ sellableQty: requestedQty, unsellableQty: 0 })
  const receiveDrafts: Record<string, ReceiveDraft> = Object.fromEntries(
    (returnRequest?.items ?? []).map((item) => [item.id, draftOverrides[item.id] ?? defaultDraft(item.requestedQty)])
  )
  const refresh = () => {
    setDraftOverrides({})
    reload()
  }

  async function handleApprove() {
    setActionLoading(true)
    setActionError(null)
    try {
      await ReturnService.approveReturn(returnId)
      refresh()
    } catch (err) {
      setActionError(apiErrorMessage(err, '核准失敗'))
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
      refresh()
    } catch (err) {
      setActionError(apiErrorMessage(err, '駁回失敗'))
    } finally {
      setActionLoading(false)
    }
  }

  function setDraft(itemId: string, field: keyof ReceiveDraft, max: number, value: number) {
    const clamped = Math.max(0, Math.min(max, Math.floor(value) || 0))
    setDraftOverrides((prev) => {
      const base = prev[itemId] ?? defaultDraft(returnRequest?.items.find((item) => item.id === itemId)?.requestedQty ?? 0)
      return { ...prev, [itemId]: { ...base, [field]: clamped } }
    })
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
      refresh()
    } catch (err) {
      setActionError(apiErrorMessage(err, '收貨確認失敗'))
    } finally {
      setActionLoading(false)
    }
  }

  if (loading) {
    return (
      <LoadingState />
    )
  }

  if (error || !returnRequest) {
    return (
      <div className="space-y-4">
        <Alert variant="destructive">
          {error || '找不到退貨申請'}
        </Alert>
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
          <PageTitle>{returnRequest.returnNumber}</PageTitle>
          <PageDescription>
            訂單 #{returnRequest.orderId.slice(0, 8)} ·{' '}
            {new Date(returnRequest.createdAt).toLocaleString('zh-TW')}
          </PageDescription>
        </div>
        <Badge variant={returnStatusBadgeVariant(returnRequest.status)}>
          {RETURN_STATUS_LABELS[returnRequest.status]}
        </Badge>
      </div>

      {actionError && (
        <Alert variant="destructive">
          {actionError}
        </Alert>
      )}

      {returnRequest.reason && (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm">買家退貨原因</CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-rs-ink whitespace-pre-wrap">
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
                <p className="text-sm font-medium text-rs-ink">
                  {item.productName ?? `品項 #${item.orderItemId.slice(0, 8)}`}
                </p>
                {item.specName && <p className="text-xs text-rs-ink-muted">{item.specName}</p>}
                <p className="text-xs text-rs-ink-muted">
                  {item.skuCode && `${item.skuCode} · `}申請退貨 {item.requestedQty} 件
                </p>
              </div>
              {returnRequest.status === 'APPROVED' ? (
                <div className="flex items-center gap-3 shrink-0">
                  <div className="flex items-center gap-1">
                    <Label htmlFor={`sellable-${item.id}`} className="text-xs text-rs-ink-muted">
                      可售
                    </Label>
                    <Input
                      id={`sellable-${item.id}`}
                      type="number"
                      min={0}
                      max={item.requestedQty}
                      value={receiveDrafts[item.id]?.sellableQty ?? 0}
                      onChange={(e) =>
                        setDraft(item.id, 'sellableQty', item.requestedQty, Number(e.target.value))
                      }
                      className="w-16 text-center"
                    />
                  </div>
                  <div className="flex items-center gap-1">
                    <Label htmlFor={`unsellable-${item.id}`} className="text-xs text-rs-ink-muted">
                      不可售
                    </Label>
                    <Input
                      id={`unsellable-${item.id}`}
                      type="number"
                      min={0}
                      max={item.requestedQty}
                      value={receiveDrafts[item.id]?.unsellableQty ?? 0}
                      onChange={(e) =>
                        setDraft(item.id, 'unsellableQty', item.requestedQty, Number(e.target.value))
                      }
                      className="w-16 text-center"
                    />
                  </div>
                </div>
              ) : (
                returnRequest.status === 'RECEIVED' && (
                  <div className="text-right text-xs text-rs-ink-muted shrink-0">
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
        <Card className="border-rs-error/30">
          <CardHeader>
            <CardTitle className="text-sm">駁回原因（選填）</CardTitle>
          </CardHeader>
          <CardContent>
            <Textarea
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
        <Card className="border-rs-error/30">
          <CardHeader>
            <CardTitle className="text-sm">駁回原因</CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-rs-ink whitespace-pre-wrap">
            {returnRequest.rejectionReason}
          </CardContent>
        </Card>
      )}
    </div>
  )
}
