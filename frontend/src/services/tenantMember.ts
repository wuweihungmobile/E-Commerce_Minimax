import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

// 對齊後端 DTO（TenantMemberResponse/TenantInviteResponse/MemberCandidateResponse，PRD §9.11，Sprint 248）
// 欄位名必須與後端完全一致（DEF-168：專案未設定 Jackson 命名策略，不會自動轉換）

export type StoreMemberRole = 'STORE_OWNER' | 'STORE_STAFF' | 'STORE_MANAGER'
export type StoreMemberStatus = 'INVITED' | 'ACTIVE'

export interface TenantMemberResponse {
  userId: string
  displayName: string
  email: string
  avatarUrl: string | null
  role: StoreMemberRole
  status: StoreMemberStatus
  joinedAt: string | null
}

export interface TenantInviteResponse {
  memberId: string
  tenantId: string
  tenantName: string
  role: StoreMemberRole
  invitedAt: string
}

export interface MemberCandidateResponse {
  userId: string
  displayName: string
  email: string
  avatarUrl: string | null
}

interface ApiResponse<T> {
  success: boolean
  code?: string
  message?: string
  data: T
}

export const STORE_MEMBER_ROLE_LABELS: Record<StoreMemberRole, string> = {
  STORE_OWNER: '店主',
  STORE_STAFF: '店員',
  STORE_MANAGER: '店長',
}

export const STORE_MEMBER_STATUS_LABELS: Record<StoreMemberStatus, string> = {
  INVITED: '邀請中',
  ACTIVE: '使用中',
}

export type StoreMemberStatusBadgeVariant = 'success' | 'warning'

export function storeMemberStatusBadgeVariant(status: StoreMemberStatus): StoreMemberStatusBadgeVariant {
  return status === 'ACTIVE' ? 'success' : 'warning'
}

class TenantMemberService {
  async listMembers(tenantId: string): Promise<TenantMemberResponse[]> {
    const response = await apiClient.get<ApiResponse<{ members: TenantMemberResponse[] }>>(
      API_ENDPOINTS.tenants.members.list(tenantId)
    )
    return response.data.data.members
  }

  async lookupByEmail(tenantId: string, email: string): Promise<MemberCandidateResponse> {
    const response = await apiClient.get<ApiResponse<MemberCandidateResponse>>(
      API_ENDPOINTS.tenants.members.lookup(tenantId, email)
    )
    return response.data.data
  }

  async inviteMember(tenantId: string, userId: string): Promise<TenantMemberResponse> {
    const response = await apiClient.post<ApiResponse<TenantMemberResponse>>(
      API_ENDPOINTS.tenants.members.invite(tenantId),
      { userId, role: 'STORE_STAFF' }
    )
    return response.data.data
  }

  async removeMember(tenantId: string, userId: string): Promise<void> {
    await apiClient.delete<ApiResponse<{ message: string }>>(
      API_ENDPOINTS.tenants.members.remove(tenantId, userId)
    )
  }

  /**
   * 帳戶頁掛載時背景呼叫（非使用者主動觸發），失敗不該有任何副作用。`_retry: true` 讓
   * `apiClient` 共用的 401 攔截器把這次請求視為「已重試過」，直接讓錯誤結束，不觸發
   * refresh token 流程——該流程失敗時會清空整個 session 並導回登入頁（見 `lib/axios.ts`），
   * 對一個非關鍵、使用者感知不到的背景查詢而言，這個全域副作用完全不成比例。
   */
  async listMyInvites(): Promise<TenantInviteResponse[]> {
    const response = await apiClient.get<ApiResponse<{ invites: TenantInviteResponse[] }>>(
      API_ENDPOINTS.tenants.members.myInvites,
      { _retry: true } as Parameters<typeof apiClient.get>[1]
    )
    return response.data.data.invites
  }

  async acceptInvite(tenantId: string): Promise<TenantMemberResponse> {
    const response = await apiClient.post<ApiResponse<TenantMemberResponse>>(
      API_ENDPOINTS.tenants.members.acceptInvite(tenantId)
    )
    return response.data.data
  }

  async declineInvite(tenantId: string): Promise<void> {
    await apiClient.post<ApiResponse<null>>(API_ENDPOINTS.tenants.members.declineInvite(tenantId))
  }
}

export default new TenantMemberService()
