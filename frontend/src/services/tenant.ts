import apiClient, { type RequestOptions } from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

// 店鋪（租戶）——店主自助端：開店申請、我的店鋪、店鋪詳情／編輯、店鋪功能開關（Sprint 331，DEF-455）。
// 這些請求原本散在 TenantApplyForm／TenantList／TenantDetail／TenantEditForm 與 dashboard 功能開關頁，各自 import apiClient。
// 平台管理員端（/v2/admin/*）見 services/admin.ts；店鋪成員見 services/tenantMember.ts。

// Tenant status enum（Sprint 146：對齊後端 Tenant.TenantStatus 實際值域，先前的 PENDING/APPROVED 從未存在）
export type TenantStatus = 'PENDING_REVIEW' | 'ACTIVE' | 'REJECTED' | 'SUSPENDED' | 'TERMINATED'

/** GET /v2/tenants/my 的一列（Sprint 146：回應形狀是 { tenants: [...] }，沒有 contactEmail/contactPhone/updatedAt，有 role/memberCount）。 */
export interface TenantSummary {
  tenantId: string
  storeName: string
  businessType: string
  status: TenantStatus
  role: string
  memberCount: number
  createdAt: string
}

/** GET /v2/tenants/{id}（對齊 TenantDetailsResponse——欄位是 tenantId 不是 id）。 */
export interface TenantDetails {
  tenantId: string
  storeName: string
  // DEF-321(b)（Sprint 252）：店鋪前台 /stores/{slug}，開店審核通過時自動產生，店主可修改
  slug: string
  // DEF-432：FRD US-M17-003 / AC-M17-003-1 允許店主修改店鋪描述（後端 ≤ 1000 字，沒填過是 null）
  storeDescription?: string | null
  businessType: string
  contactEmail: string
  contactPhone: string | null
  status: TenantStatus
  createdAt: string
  updatedAt: string
  purchaseOrderApprovalThreshold?: number | null
}

export interface TenantApplyRequest {
  storeName: string
  businessType: string
  contactEmail: string
  contactPhone: string
  /** DEF-432：店鋪描述是選填（PRD §9.10.1），留空要傳 undefined（不送出），後端才會是「沒填」(null) 而不是空字串。 */
  storeDescription?: string
}

/** PUT /v2/tenants/{id}：storeDescription／purchaseOrderApprovalThreshold 沒有要改就不要帶（後端 null＝不變更）。 */
export interface TenantUpdateRequest {
  storeName: string
  slug: string
  businessType: string
  contactEmail: string
  contactPhone: string
  storeDescription?: string
  purchaseOrderApprovalThreshold?: number
}

// 欄位名必須與後端 FeatureToggleResponse.FeatureInfo 完全一致（DEF-168：專案未配置任何 Jackson 命名策略，不會自動轉換）
export interface FeatureToggle {
  featureKey: string
  category: string
  featureName: string
  description: string
  isEnabled: boolean
  status: 'ACTIVE' | 'PENDING' | 'INACTIVE'
  requiresAdminReview: boolean
}

// Sprint 153（S147 §6 範圍外項目）：數值配額用量，與 features 分開回傳
export interface QuotaInfo {
  featureKey: string
  featureName: string
  limit: number
  currentUsage: number
}

export interface FeatureToggleResponse {
  features: FeatureToggle[]
  quotas: QuotaInfo[]
}

/** 後端 FeatureToggleUpdateResponse（DEF-168）。需審核的功能送出啟用申請後，newState 仍是 false、status 是 PENDING_APPROVAL（DEF-428）。 */
export interface FeatureToggleUpdateResult {
  featureKey: string
  previousState: boolean
  newState: boolean
  status: string
  statusDescription: string
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

class TenantService {
  async apply(request: TenantApplyRequest): Promise<void> {
    await apiClient.post(API_ENDPOINTS.tenants.apply, request)
  }

  async listMine(options?: RequestOptions): Promise<TenantSummary[]> {
    const response = await apiClient.get<ApiResponse<{ tenants: TenantSummary[] }>>(API_ENDPOINTS.tenants.list, options)
    return response.data.data?.tenants || []
  }

  async getById(tenantId: string, options?: RequestOptions): Promise<TenantDetails> {
    const response = await apiClient.get<ApiResponse<TenantDetails>>(API_ENDPOINTS.tenants.detail(tenantId), options)
    return response.data.data
  }

  async update(tenantId: string, request: TenantUpdateRequest): Promise<void> {
    await apiClient.put(API_ENDPOINTS.tenants.update(tenantId), request)
  }

  /** 呼叫者自己店鋪的功能開關與配額（店主自助頁）。 */
  async getMyFeatures(options?: RequestOptions): Promise<FeatureToggleResponse> {
    const response = await apiClient.get<ApiResponse<FeatureToggleResponse>>(API_ENDPOINTS.dashboard.tenants.features, options)
    return { features: response.data.data.features, quotas: response.data.data.quotas ?? [] }
  }

  async updateMyFeature(feature: string, enabled: boolean): Promise<FeatureToggleUpdateResult> {
    const response = await apiClient.put<ApiResponse<FeatureToggleUpdateResult>>(
      API_ENDPOINTS.dashboard.tenants.updateFeature(feature),
      { enabled }
    )
    return response.data.data
  }
}

const tenantService = new TenantService()
export default tenantService
