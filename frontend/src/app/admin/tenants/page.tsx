'use client'

import { useState, useEffect, useCallback } from 'react'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Pagination } from '@/components/ui/pagination'
import apiClient from '@/lib/axios'

// Tenant interface - matches actual API response
interface Tenant {
  tenantId: string
  name: string
  status: 'PENDING_REVIEW' | 'ACTIVE' | 'SUSPENDED' | 'TERMINATED'
  userCount?: number
  listingCount?: number
  createdAt: string
  updatedAt: string
  rejectReason?: string
}

/**
 * 待審核的開店申請（`tenant_applications` 表）。
 *
 * 🔴 這是與 `Tenant` **不同的實體**，兩者不可混用（Sprint 109，DEF-060）：
 * 網友送出的開店申請寫入 `tenant_applications`（status=PENDING），要等 Admin 核准，
 * 後端才會建立真正的 `Tenant`（直接就是 ACTIVE）。因此「待審核」分頁必須查
 * `/v2/admin/tenant-applications`，**不可**查 `/v2/admin/tenants?status=PENDING_REVIEW`
 * ——生產環境沒有任何路徑會讓 `Tenant` 進入 `PENDING_REVIEW`，那樣查永遠是空的，
 * 也正是本頁在 Sprint 109 之前的缺陷（申請送出後 Admin 永遠看不到）。
 */
interface TenantApplication {
  applicationId: string
  userId: string
  storeName: string
  storeDescription?: string
  businessType: string
  contactEmail?: string
  contactPhone?: string
  status: string
  submittedAt: string
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

interface TenantListResponse {
  tenants: Tenant[]
  totalCount: number
  page: number
  size: number
  totalElements: number
  totalPages: number
}

interface TenantApplicationListResponse {
  applications: TenantApplication[]
}

type FilterTab = 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'

// PENDING 不在此表中：該分頁查的是「開店申請」而非「租戶」，見 TenantApplication 的註解。
const FILTER_TO_STATUS: Record<FilterTab, Tenant['status'] | undefined> = {
  ALL: undefined,
  PENDING: undefined,
  APPROVED: 'ACTIVE',
  REJECTED: 'TERMINATED',
}

const PAGE_SIZE = 20
const APPLICATIONS_URL = '/v2/admin/tenant-applications'

function extractErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === 'object' && 'response' in err) {
    const axiosErr = err as { response?: { data?: { message?: string } } }
    return axiosErr.response?.data?.message || fallback
  }
  return fallback
}

export default function AdminTenantListPage() {
  const [tenants, setTenants] = useState<Tenant[]>([])
  const [applications, setApplications] = useState<TenantApplication[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<FilterTab>('ALL')
  const [keyword, setKeyword] = useState('')
  const [currentPage, setCurrentPage] = useState(1) // 1-based，供 Pagination 元件使用
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)
  // 審核動作的結果訊息。刻意**不用原生 alert()**：alert 會讓 Playwright 的 teardown
  // 崩潰（既有 flaky 的已知成因），且畫面內訊息才可被 E2E 斷言。
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionId, setActionId] = useState<string | null>(null)
  const [rejectingId, setRejectingId] = useState<string | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  // 各分頁籤的數量，伺服器端篩選下需分別查詢才能同時顯示
  const [tabCounts, setTabCounts] = useState<Record<FilterTab, number>>({
    ALL: 0,
    PENDING: 0,
    APPROVED: 0,
    REJECTED: 0,
  })

  const fetchTenants = useCallback(async (page: number) => {
    setLoading(true)
    setError(null)

    try {
      const params: Record<string, string | number> = {
        page: page - 1, // 後端 page 為 0-based
        size: PAGE_SIZE,
      }
      const status = FILTER_TO_STATUS[filter]
      if (status) params.status = status
      if (keyword) params.keyword = keyword

      const response = await apiClient.get<ApiResponse<TenantListResponse>>('/v2/admin/tenants', { params })
      const data = response.data.data
      setTenants(data?.tenants || [])
      setTotalPages(data?.totalPages || 1)
      setTotalElements(data?.totalElements ?? data?.totalCount ?? 0)
    } catch (err: unknown) {
      setError(extractErrorMessage(err, '載入店鋪列表失敗'))
    } finally {
      setLoading(false)
    }
  }, [filter, keyword])

  const fetchApplications = useCallback(async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await apiClient.get<ApiResponse<TenantApplicationListResponse>>(APPLICATIONS_URL)
      const list = response.data.data?.applications || []
      setApplications(list)
      setTotalPages(1) // 後端此端點回傳全部待審核申請，無分頁
      setTotalElements(list.length)
    } catch (err: unknown) {
      setError(extractErrorMessage(err, '載入開店申請失敗'))
    } finally {
      setLoading(false)
    }
  }, [])

  const fetchTabCounts = useCallback(async () => {
    try {
      const tenantTabs: FilterTab[] = ['ALL', 'APPROVED', 'REJECTED']
      const [applicationsRes, ...tenantResults] = await Promise.all([
        apiClient.get<ApiResponse<TenantApplicationListResponse>>(APPLICATIONS_URL),
        ...tenantTabs.map((tab) => {
          const status = FILTER_TO_STATUS[tab]
          const params: Record<string, string | number> = { page: 0, size: 1 }
          if (status) params.status = status
          return apiClient.get<ApiResponse<TenantListResponse>>('/v2/admin/tenants', { params })
        }),
      ])
      const counts = {} as Record<FilterTab, number>
      counts.PENDING = applicationsRes.data.data?.applications?.length ?? 0
      tenantTabs.forEach((tab, i) => {
        const data = tenantResults[i].data.data
        counts[tab] = data?.totalElements ?? data?.totalCount ?? 0
      })
      setTabCounts(counts)
    } catch (err) {
      console.error('Failed to load tab counts:', err)
    }
  }, [])

  useEffect(() => {
    fetchTabCounts()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (filter === 'PENDING') {
      fetchApplications()
    } else {
      fetchTenants(currentPage)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentPage, filter, keyword])

  const handleFilterChange = (tab: FilterTab) => {
    setFilter(tab)
    setCurrentPage(1)
    setActionMessage(null)
    setActionError(null)
    setRejectingId(null)
    setRejectReason('')
  }

  const refresh = () => {
    if (filter === 'PENDING') {
      fetchApplications()
    } else {
      fetchTenants(currentPage)
    }
    fetchTabCounts()
  }

  const handleApprove = async (application: TenantApplication) => {
    setActionId(application.applicationId)
    setActionMessage(null)
    setActionError(null)
    try {
      await apiClient.post<ApiResponse<unknown>>(
        `${APPLICATIONS_URL}/${application.applicationId}/approve`
      )
      setActionMessage(`已核准「${application.storeName}」，店鋪已建立並啟用`)
      await fetchApplications()
      await fetchTabCounts()
    } catch (err: unknown) {
      setActionError(extractErrorMessage(err, '核准失敗'))
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
      await apiClient.post<ApiResponse<unknown>>(
        `${APPLICATIONS_URL}/${application.applicationId}/reject`,
        { reason: rejectReason }
      )
      setActionMessage(`已駁回「${application.storeName}」`)
      setRejectingId(null)
      setRejectReason('')
      await fetchApplications()
      await fetchTabCounts()
    } catch (err: unknown) {
      setActionError(extractErrorMessage(err, '駁回失敗'))
    } finally {
      setActionId(null)
    }
  }

  const getStatusBadge = (status: Tenant['status']) => {
    const statusConfig: Record<Tenant['status'], { variant: 'default' | 'secondary' | 'destructive' | 'success' | 'outline'; label: string }> = {
      PENDING_REVIEW: { variant: 'secondary', label: '審核中' },
      ACTIVE: { variant: 'success', label: '已核准' },
      SUSPENDED: { variant: 'destructive', label: '已停權' },
      TERMINATED: { variant: 'destructive', label: '已終止' },
    }
    const config = statusConfig[status] || { variant: 'outline', label: status }
    return <Badge variant={config.variant}>{config.label}</Badge>
  }

  if (error) {
    return (
      <div className="space-y-4">
        <div className="bg-error/10 border border-error/20 text-error px-4 py-3 rounded-md">
          {error}
        </div>
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
          <h1 className="text-2xl font-bold">店鋪管理</h1>
          <p className="text-muted-foreground">
            {tabCounts.PENDING > 0 ? (
              <span className="text-warning">有待審核開店申請 {tabCounts.PENDING} 件</span>
            ) : (
              '目前無待審核開店申請'
            )}
          </p>
        </div>
        <Button variant="outline" onClick={refresh}>
          重新整理
        </Button>
      </div>

      {/* 審核動作結果 */}
      {actionMessage && (
        <div
          data-testid="action-message"
          className="bg-success/10 border border-success/20 text-success px-4 py-3 rounded-md"
        >
          {actionMessage}
        </div>
      )}
      {actionError && (
        <div
          data-testid="action-error"
          className="bg-error/10 border border-error/20 text-error px-4 py-3 rounded-md"
        >
          {actionError}
        </div>
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
            <label className="text-sm text-muted-foreground mb-1 block">搜尋店鋪名稱</label>
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
        <div className="flex items-center justify-center h-64">
          <div className="text-gray-500">載入中...</div>
        </div>
      ) : showApplications ? (
        /* ── 待審核開店申請 ────────────────────────────── */
        applications.length === 0 ? (
          <Card>
            <CardContent className="flex flex-col items-center justify-center h-48">
              <p className="text-gray-500 mb-4">目前沒有待審核的開店申請</p>
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-4">
            {applications.map((application) => (
              <Card
                key={application.applicationId}
                data-testid="application-card"
                className="border-warning"
              >
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle className="text-lg">{application.storeName}</CardTitle>
                      <CardDescription>
                        申請時間：{new Date(application.submittedAt).toLocaleDateString('zh-TW')}
                      </CardDescription>
                    </div>
                    <Badge variant="secondary">審核中</Badge>
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
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-gray-500 mb-4">
              {filter === 'ALL' ? '尚無店鋪資料' : '無符合條件的店鋪'}
            </p>
          </CardContent>
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
                  {getStatusBadge(tenant.status)}
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
