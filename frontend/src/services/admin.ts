import apiClient, { type RequestOptions } from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'
import type { FeatureToggleResponse, TenantStatus } from '@/services/tenant'

// 平台管理員端（/v2/admin/*）：平台統計、店鋪與開店申請審核、店鋪功能開關、採購單審批、稽核紀錄（Sprint 331，DEF-455）。
// 這些請求原本散在 admin/ 底下 6 個頁面，各自 import apiClient。
// 結算沖正與客服工單的 admin 端點仍在 services/settlement.ts、services/support.ts（既有位置，這輪不動）。

export interface PlatformStats {
  totalTenants: number
  activeTenants: number
  totalUsers: number
  totalListings: number
  totalOrders: number
  pendingTenantReviews: number
}

/** 管理員視角的店鋪（GET /v2/admin/tenants）。注意與店主端的 TenantSummary／TenantDetails 欄位不同（這裡是 name 不是 storeName）。 */
export interface AdminTenant {
  tenantId: string
  name: string
  status: Exclude<TenantStatus, 'REJECTED'>
  userCount?: number
  listingCount?: number
  createdAt: string
  updatedAt: string
  rejectReason?: string
}

export interface AdminTenantListParams {
  /** 後端 page 為 0-based */
  page: number
  size: number
  status?: AdminTenant['status']
  keyword?: string
}

export interface AdminTenantList {
  tenants: AdminTenant[]
  totalCount: number
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/**
 * 待審核的開店申請（`tenant_applications` 表）。
 *
 * 🔴 這是與 `Tenant` **不同的實體**，兩者不可混用（Sprint 109，DEF-060）：
 * 網友送出的開店申請寫入 `tenant_applications`（status=PENDING），要等 Admin 核准，
 * 後端才會建立真正的 `Tenant`（直接就是 ACTIVE）。因此「待審核」分頁必須查
 * `/v2/admin/tenant-applications`，**不可**查 `/v2/admin/tenants?status=PENDING_REVIEW`
 * ——生產環境沒有任何路徑會讓 `Tenant` 進入 `PENDING_REVIEW`，那樣查永遠是空的。
 */
export interface TenantApplication {
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

export interface PurchaseOrderSummary {
  id: string
  tenantId: string
  poNumber: string
  status: string
  totalAmount: number
  currency: string
  submittedAt: string
  reviewedBy?: string
  reviewedAt?: string
  rejectionReason?: string
}

export interface PendingPurchaseOrderList {
  purchaseOrders: PurchaseOrderSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AuditLog {
  id: string
  tenantId: string | null
  userId: string | null
  action: string
  entityType: string | null
  entityId: string | null
  oldValue: string | null
  newValue: string | null
  reason: string | null
  createdAt: string
}

export interface AuditLogList {
  logs: AuditLog[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AuditLogQuery {
  /** 後端 page 為 0-based */
  page: number
  size: number
  action?: string
  startDate?: string
  endDate?: string
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

class AdminService {
  async getPlatformStats(options?: RequestOptions): Promise<PlatformStats> {
    const response = await apiClient.get<ApiResponse<PlatformStats>>(API_ENDPOINTS.admin.stats, options)
    return response.data.data
  }

  // ---- 店鋪 ----
  async listTenants(params: AdminTenantListParams, options?: RequestOptions): Promise<AdminTenantList> {
    const response = await apiClient.get<ApiResponse<AdminTenantList>>(API_ENDPOINTS.admin.tenants.list, { params, ...options })
    return response.data.data
  }

  async getTenant(tenantId: string, options?: RequestOptions): Promise<AdminTenant> {
    const response = await apiClient.get<ApiResponse<AdminTenant>>(API_ENDPOINTS.admin.tenants.detail(tenantId), options)
    return response.data.data
  }

  async getTenantFeatures(tenantId: string, options?: RequestOptions): Promise<FeatureToggleResponse> {
    const response = await apiClient.get<ApiResponse<FeatureToggleResponse>>(API_ENDPOINTS.admin.tenants.features(tenantId), options)
    return { features: response.data.data.features, quotas: response.data.data.quotas ?? [] }
  }

  /** Admin 的設定即為最終決定：直接生效，不會進入「待審核」（那是店主自助申請才有的限制）。 */
  async updateTenantFeature(tenantId: string, feature: string, enabled: boolean): Promise<void> {
    await apiClient.put(API_ENDPOINTS.admin.tenants.updateFeature(tenantId, feature), { enabled })
  }

  // ---- 開店申請 ----
  async listTenantApplications(options?: RequestOptions): Promise<TenantApplication[]> {
    const response = await apiClient.get<ApiResponse<{ applications: TenantApplication[] }>>(
      API_ENDPOINTS.admin.tenantApplications.list,
      options
    )
    return response.data.data?.applications || []
  }

  async approveTenantApplication(applicationId: string): Promise<void> {
    await apiClient.post(API_ENDPOINTS.admin.tenantApplications.approve(applicationId))
  }

  async rejectTenantApplication(applicationId: string, reason: string): Promise<void> {
    await apiClient.post(API_ENDPOINTS.admin.tenantApplications.reject(applicationId), { reason })
  }

  // ---- 採購單審批（M16）----
  /** 不帶 params 時由後端用預設分頁（總覽頁只取 totalElements）。 */
  async listPendingPurchaseOrders(
    params?: { page: number; size: number },
    options?: RequestOptions
  ): Promise<PendingPurchaseOrderList> {
    const response = await apiClient.get<ApiResponse<PendingPurchaseOrderList>>(
      API_ENDPOINTS.admin.purchaseOrders.pending,
      params || options ? { params, ...options } : undefined
    )
    return response.data.data
  }

  async approvePurchaseOrder(id: string): Promise<void> {
    await apiClient.post(API_ENDPOINTS.admin.purchaseOrders.approve(id))
  }

  async rejectPurchaseOrder(id: string, reason: string): Promise<void> {
    await apiClient.post(API_ENDPOINTS.admin.purchaseOrders.reject(id), { reason })
  }

  // ---- 稽核紀錄 ----
  async listAuditLogs(query: AuditLogQuery, options?: RequestOptions): Promise<AuditLogList> {
    const response = await apiClient.get<ApiResponse<AuditLogList>>(API_ENDPOINTS.admin.auditLogs, { params: query, ...options })
    return response.data.data
  }
}

const adminService = new AdminService()
export default adminService
