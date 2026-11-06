'use client'

import { useState } from 'react'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Pagination } from '@/components/ui/pagination'
import adminService, { type AdminTenant as Tenant, type AdminTenantListParams, type TenantApplication } from '@/services/admin'
import { apiErrorMessage } from '@/lib/api-error'
import { useAsyncData } from '@/hooks/useAsyncData'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { EmptyState, LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'
import { StatusBadge } from '@/components/ui/status-badge'

// 「待審核」分頁查的是開店申請（TenantApplication），不是店鋪（Tenant）——兩者是不同實體，說明見 services/admin.ts。

type FilterTab = 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'

// PENDING 不在此表中：該分頁查的是「開店申請」而非「租戶」，見 TenantApplication 的註解。
const FILTER_TO_STATUS: Record<FilterTab, Tenant['status'] | undefined> = {
  ALL: undefined,
  PENDING: undefined,
  APPROVED: 'ACTIVE',
  REJECTED: 'TERMINATED',
}

const PAGE_SIZE = 20

export default function AdminTenantListPage() {
  const [filter, setFilter] = useState<FilterTab>('ALL')
  const [keyword, setKeyword] = useState('')
  const [currentPage, setCurrentPage] = useState(1) // 1-based，供 Pagination 元件使用
  // 審核動作的結果訊息。刻意**不用原生 alert()**：alert 會讓 Playwright 的 teardown
  // 崩潰（既有 flaky 的已知成因），且畫面內訊息才可被 E2E 斷言。
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionId, setActionId] = useState<string | null>(null)
  const [rejectingId, setRejectingId] = useState<string | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  // 清單：「審核中」分頁查開店申請，其餘分頁查店鋪（伺服器端篩選＋分頁）。條件一變就重抓，且只認最後一次請求的結果。
  const { data: list, error, loading, reload: reloadList } = useAsyncData(
    async (signal) => {
      if (filter === 'PENDING') {
        const applications = await adminService.listTenantApplications({ signal })
        // 後端此端點回傳全部待審核申請，無分頁
        return { tenants: [] as Tenant[], applications, totalPages: 1, totalElements: applications.length }
      }
      const params: AdminTenantListParams = {
        page: currentPage - 1, // 後端 page 為 0-based
        size: PAGE_SIZE,
      }
      const status = FILTER_TO_STATUS[filter]
      if (status) params.status = status
      if (keyword) params.keyword = keyword

      const data = await adminService.listTenants(params, { signal })
      return {
        tenants: data?.tenants || [],
        applications: [] as TenantApplication[],
        totalPages: data?.totalPages || 1,
        totalElements: data?.totalElements ?? data?.totalCount ?? 0,
      }
    },
    [filter, currentPage, keyword],
    { errorMessage: ({ message }) => message || (filter === 'PENDING' ? '載入開店申請失敗' : '載入店鋪列表失敗') }
  )
  const tenants = list?.tenants ?? []
  const applications = list?.applications ?? []
  const totalPages = list?.totalPages ?? 1
  const totalElements = list?.totalElements ?? 0

  // 各分頁籤的數量，伺服器端篩選下需分別查詢才能同時顯示。失敗只記錄、維持上一次的數字（不顯示錯誤畫面）
  const { data: tabCountsData, reload: reloadTabCounts } = useAsyncData(
    async (signal) => {
      const tenantTabs: FilterTab[] = ['ALL', 'APPROVED', 'REJECTED']
      const [pendingApplications, ...tenantResults] = await Promise.all([
        adminService.listTenantApplications({ signal }),
        ...tenantTabs.map((tab) => {
          const status = FILTER_TO_STATUS[tab]
          const params: AdminTenantListParams = { page: 0, size: 1 }
          if (status) params.status = status
          return adminService.listTenants(params, { signal })
        }),
      ])
      const counts = {} as Record<FilterTab, number>
      counts.PENDING = pendingApplications.length
      tenantTabs.forEach((tab, i) => {
        const data = tenantResults[i]
        counts[tab] = data?.totalElements ?? data?.totalCount ?? 0
      })
      return counts
    },
    [],
    {
      errorMessage: '載入分頁籤數量失敗',
      onError: (_message, err) => console.error('Failed to load tab counts:', err),
    }
  )
  const tabCounts: Record<FilterTab, number> = tabCountsData ?? { ALL: 0, PENDING: 0, APPROVED: 0, REJECTED: 0 }

  const handleFilterChange = (tab: FilterTab) => {
    setFilter(tab)
    setCurrentPage(1)
    setActionMessage(null)
    setActionError(null)
    setRejectingId(null)
    setRejectReason('')
  }

  const refresh = () => {
    reloadList()
    reloadTabCounts()
  }

  const handleApprove = async (application: TenantApplication) => {
    setActionId(application.applicationId)
    setActionMessage(null)
    setActionError(null)
    try {
      await adminService.approveTenantApplication(application.applicationId)
      setActionMessage(`已核准「${application.storeName}」，店鋪已建立並啟用`)
      refresh()
    } catch (err: unknown) {
      setActionError(apiErrorMessage(err, '核准失敗'))
    } finally {
      setActionId(null)
    }
  }

  const handleReject = async (application: TenantApplication) => {
    if (!rejectReason.trim()) {
      setActionError('請填寫駁回原因')
      return
    }

    setActionId(application.applicationId)
    setActionMessage(null)
    setActionError(null)
    try {
      await adminService.rejectTenantApplication(application.applicationId, rejectReason)
      setActionMessage(`已駁回「${application.storeName}」`)
      setRejectingId(null)
      setRejectReason('')
      refresh()
    } catch (err: unknown) {
      setActionError(apiErrorMessage(err, '駁回失敗'))
    } finally {
      setActionId(null)
    }
  }

  if (error) {
    return (
      <div className="space-y-4">
        <Alert variant="destructive">
          {error}
        </Alert>
        <Button variant="outline" onClick={refresh}>
          重試
        </Button>
      </div>
    )
  }

  const showApplications = filter === 'PENDING'

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <PageTitle>店鋪管理</PageTitle>
          <PageDescription>
            {tabCounts.PENDING > 0 ? (
              <span className="font-medium text-rs-ink">有待審核開店申請 {tabCounts.PENDING} 件</span>
            ) : (
              '目前無待審核開店申請'
            )}
          </PageDescription>
        </div>
        <Button variant="outline" onClick={refresh}>
          重新整理
        </Button>
      </div>

      {/* 審核動作結果 */}
      {actionMessage && (
        <Alert
          variant="success"
          data-testid="action-message"
        >
          {actionMessage}
        </Alert>
      )}
      {actionError && (
        <Alert
          variant="destructive"
          data-testid="action-error"
        >
          {actionError}
        </Alert>
      )}

      {/* Filter Tabs */}
      <div className="flex gap-2">
        <Button
          variant={filter === 'ALL' ? 'default' : 'outline'}
          size="sm"
          onClick={() => handleFilterChange('ALL')}
        >
          全部 ({tabCounts.ALL})
        </Button>
        <Button
          data-testid="tab-pending"
          variant={filter === 'PENDING' ? 'default' : 'outline'}
          size="sm"
          onClick={() => handleFilterChange('PENDING')}
        >
          審核中 ({tabCounts.PENDING})
        </Button>
        <Button
          variant={filter === 'APPROVED' ? 'default' : 'outline'}
          size="sm"
          onClick={() => handleFilterChange('APPROVED')}
        >
          已核准 ({tabCounts.APPROVED})
        </Button>
        <Button
          variant={filter === 'REJECTED' ? 'default' : 'outline'}
          size="sm"
          onClick={() => handleFilterChange('REJECTED')}
        >
          已拒絕 ({tabCounts.REJECTED})
        </Button>
      </div>

      {/* Keyword Search（僅適用於租戶列表；申請列表由後端一次回傳全部待審核件） */}
      {!showApplications && (
        <div className="flex gap-2 items-end">
          <div className="flex-1 max-w-xs">
            <Label className="text-muted-foreground mb-1 block">搜尋店鋪名稱</Label>
            <Input
              placeholder="輸入店鋪名稱或 slug"
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value)
                setCurrentPage(1)
              }}
            />
          </div>
        </div>
      )}

      {loading ? (
        <LoadingState />
      ) : showApplications ? (
        /* ── 待審核開店申請 ────────────────────────────── */
        applications.length === 0 ? (
          <Card>
            <EmptyState title="目前沒有待審核的開店申請" className="h-48" />
          </Card>
        ) : (
          <div className="space-y-4">
            {applications.map((application) => (
              <Card
                key={application.applicationId}
                data-testid="application-card"
                className="border-rs-warning/40"
              >
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle className="text-lg">{application.storeName}</CardTitle>
                      <CardDescription>
                        申請時間：{new Date(application.submittedAt).toLocaleDateString('zh-TW')}
                      </CardDescription>
                    </div>
                    <StatusBadge domain="tenant" status="PENDING_REVIEW" />
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
                    <div>
                      <span className="text-muted-foreground">經營類型：</span>
                      {application.businessType}
                    </div>
                    <div>
                      <span className="text-muted-foreground">聯絡 Email：</span>
                      {application.contactEmail || '—'}
                    </div>
                    <div>
                      <span className="text-muted-foreground">聯絡電話：</span>
                      {application.contactPhone || '—'}
                    </div>
                  </div>
                  {application.storeDescription && (
                    <p className="mt-4 text-sm text-muted-foreground">
                      {application.storeDescription}
                    </p>
                  )}

                  {rejectingId === application.applicationId && (
                    <div className="mt-4 space-y-2">
                      <Label htmlFor={`rejectReason-${application.applicationId}`}>
                        駁回原因 *
                      </Label>
                      <Input
                        id={`rejectReason-${application.applicationId}`}
                        data-testid="reject-reason"
                        value={rejectReason}
                        onChange={(e) => setRejectReason(e.target.value)}
                        placeholder="請輸入駁回原因..."
                      />
                    </div>
                  )}
                </CardContent>
                <CardFooter className="flex gap-2">
                  {rejectingId === application.applicationId ? (
                    <>
                      <Button
                        data-testid="confirm-reject"
                        variant="destructive"
                        size="sm"
                        onClick={() => handleReject(application)}
                        disabled={actionId !== null || !rejectReason.trim()}
                      >
                        {actionId === application.applicationId ? '處理中...' : '確認駁回'}
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setRejectingId(null)
                          setRejectReason('')
                        }}
                        disabled={actionId !== null}
                      >
                        取消
                      </Button>
                    </>
                  ) : (
                    <>
                      <Button
                        data-testid="approve-application"
                        variant="default"
                        size="sm"
                        onClick={() => handleApprove(application)}
                        disabled={actionId !== null}
                      >
                        {actionId === application.applicationId ? '處理中...' : '核准'}
                      </Button>
                      <Button
                        data-testid="reject-application"
                        variant="destructive"
                        size="sm"
                        onClick={() => {
                          setRejectingId(application.applicationId)
                          setRejectReason('')
                          setActionError(null)
                        }}
                        disabled={actionId !== null}
                      >
                        駁回
                      </Button>
                    </>
                  )}
                </CardFooter>
              </Card>
            ))}
          </div>
        )
      ) : /* ── 既有店鋪（Tenant）────────────────────────── */
      tenants.length === 0 ? (
        <Card>
          <EmptyState title={filter === 'ALL' ? '尚無店鋪資料' : '無符合條件的店鋪'} className="h-48" />
        </Card>
      ) : (
        <div className="space-y-4">
          {tenants.map((tenant) => (
            <Card key={tenant.tenantId}>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div>
                    <CardTitle className="text-lg">{tenant.name}</CardTitle>
                    <CardDescription>
                      建立時間：{new Date(tenant.createdAt).toLocaleDateString('zh-TW')}
                    </CardDescription>
                  </div>
                  <StatusBadge domain="tenant" status={tenant.status} />
                </div>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
                  <div>
                    <span className="text-muted-foreground">店鋪 ID：</span>
                    <span className="font-mono text-xs">{tenant.tenantId}</span>
                  </div>
                </div>
              </CardContent>
              <CardFooter className="flex gap-2">
                <Link href={`/admin/tenants/${tenant.tenantId}/review`}>
                  <Button variant="outline" size="sm">
                    店鋪詳情
                  </Button>
                </Link>
              </CardFooter>
            </Card>
          ))}
        </div>
      )}

      {totalElements > 0 && (
        <p className="text-sm text-muted-foreground text-center">
          {showApplications ? `共 ${totalElements} 件待審核申請` : `共 ${totalElements} 間店鋪`}
        </p>
      )}
      {!showApplications && (
        <Pagination current={currentPage} total={totalPages} onChange={setCurrentPage} />
      )}
    </div>
  )
}
