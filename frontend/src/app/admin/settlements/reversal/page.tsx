'use client'

import { useState, useEffect, useCallback } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Pagination } from '@/components/ui/pagination'
import SettlementService, { SettlementStatementDto } from '@/services/settlement'
import AuthService from '@/services/auth'

const PAGE_SIZE = 20

export default function AdminSettlementReversalPage() {
  const [statements, setStatements] = useState<SettlementStatementDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionLoadingId, setActionLoadingId] = useState<string | null>(null)
  const [initiatingId, setInitiatingId] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const [currentPage, setCurrentPage] = useState(1) // 1-based，供 Pagination 元件使用
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)

  const currentRole = AuthService.getCurrentUser()?.role

  const fetchStatements = useCallback(async (page: number) => {
    setLoading(true)
    setError(null)
    try {
      const data = await SettlementService.getReversalCandidates(undefined, page - 1, PAGE_SIZE)
      setStatements(data?.statements || [])
      setTotalPages(data?.totalPages || 1)
      setTotalElements(data?.totalElements || 0)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入結算單失敗')
      } else {
        setError('載入結算單失敗')
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchStatements(currentPage)
  }, [currentPage, fetchStatements])

  const handleInitiate = async (statementId: string) => {
    if (!reason.trim()) {
      alert('請填寫逆轉原因')
      return
    }
    setActionLoadingId(statementId)
    try {
      await SettlementService.initiateReversal(statementId, reason)
      setInitiatingId(null)
      setReason('')
      fetchStatements(currentPage)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        alert(axiosErr.response?.data?.message || '發起逆轉失敗')
      } else {
        alert('發起逆轉失敗')
      }
    } finally {
      setActionLoadingId(null)
    }
  }

  const handleConfirm = async (statementId: string) => {
    if (!confirm('確定要確認此結算單逆轉嗎？確認後將產生沖銷用的 Credit Note，無法復原。')) return
    setActionLoadingId(statementId)
    try {
      await SettlementService.confirmReversal(statementId)
      fetchStatements(currentPage)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        alert(axiosErr.response?.data?.message || '確認逆轉失敗')
      } else {
        alert('確認逆轉失敗')
      }
    } finally {
      setActionLoadingId(null)
    }
  }

  const formatAmount = (amount: number, currency: string) => {
    return new Intl.NumberFormat('zh-TW', { style: 'currency', currency: currency || 'TWD' }).format(amount)
  }

  if (error) {
    return (
      <div className="space-y-4">
        <div className="bg-error/10 border border-error/20 text-error px-4 py-3 rounded-md">{error}</div>
        <Button variant="outline" onClick={() => fetchStatements(currentPage)}>重試</Button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">結算單逆轉</h1>
        <p className="text-muted-foreground">
          {totalElements > 0 ? <span className="text-warning">可操作結算單 {totalElements} 筆</span> : '目前無可發起/確認逆轉的結算單'}
        </p>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="text-gray-500">載入中...</div>
        </div>
      ) : statements.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-gray-500">目前無可操作的結算單</p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {statements.map((statement) => {
            const canConfirm = statement.status === 'REVERSAL_PENDING'
              && statement.reversalInitiatedByRole !== currentRole
            return (
              <Card key={statement.id} className="border-warning">
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle className="text-lg">{statement.statementNumber}</CardTitle>
                      <CardDescription>
                        結算期間：{statement.periodStart} ~ {statement.periodEnd}
                      </CardDescription>
                    </div>
                    <Badge variant="warning">
                      {statement.status === 'PAID' ? '待發起逆轉' : '待確認逆轉'}
                    </Badge>
                  </div>
                </CardHeader>
                <CardContent className="space-y-2">
                  <div className="flex justify-between text-sm">
                    <span className="text-muted-foreground">結算淨額：</span>
                    <span className="font-bold">{formatAmount(statement.netSettlementAmount, statement.currency)}</span>
                  </div>
                  {statement.status === 'REVERSAL_PENDING' && (
                    <>
                      <div className="flex justify-between text-sm">
                        <span className="text-muted-foreground">發起角色：</span>
                        <span>{statement.reversalInitiatedByRole}</span>
                      </div>
                      <div className="flex justify-between text-sm">
                        <span className="text-muted-foreground">逆轉原因：</span>
                        <span>{statement.reversalReason || '-'}</span>
                      </div>
                    </>
                  )}
                </CardContent>
                <CardFooter className="flex flex-col gap-4 items-stretch">
                  {statement.status === 'PAID' && (
                    <div className="flex gap-2">
                      <Button
                        variant="destructive"
                        onClick={() => { setInitiatingId(statement.id); setReason('') }}
                        disabled={actionLoadingId === statement.id}
                      >
                        發起逆轉
                      </Button>
                    </div>
                  )}

                  {statement.status === 'REVERSAL_PENDING' && (
                    <div className="flex flex-col gap-2">
                      <Button
                        variant="destructive"
                        onClick={() => handleConfirm(statement.id)}
                        disabled={actionLoadingId === statement.id || !canConfirm}
                      >
                        {actionLoadingId === statement.id ? '處理中...' : '確認逆轉'}
                      </Button>
                      {!canConfirm && (
                        <p className="text-xs text-muted-foreground">
                          確認人須與發起人（{statement.reversalInitiatedByRole}）不同角色，雙重授權機制
                        </p>
                      )}
                    </div>
                  )}

                  {initiatingId === statement.id && (
                    <div className="space-y-2 border-t pt-4">
                      <Label htmlFor={`reason-${statement.id}`}>逆轉原因 *</Label>
                      <Input
                        id={`reason-${statement.id}`}
                        value={reason}
                        onChange={(e) => setReason(e.target.value)}
                        placeholder="請輸入逆轉原因..."
                      />
                      <div className="flex gap-2">
                        <Button
                          variant="destructive"
                          size="sm"
                          onClick={() => handleInitiate(statement.id)}
                          disabled={actionLoadingId === statement.id || !reason.trim()}
                        >
                          確認發起
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => { setInitiatingId(null); setReason('') }}
                          disabled={actionLoadingId === statement.id}
                        >
                          取消
                        </Button>
                      </div>
                    </div>
                  )}
                </CardFooter>
              </Card>
            )
          })}
        </div>
      )}

      {totalPages > 1 && <Pagination current={currentPage} total={totalPages} onChange={setCurrentPage} />}
    </div>
  )
}
