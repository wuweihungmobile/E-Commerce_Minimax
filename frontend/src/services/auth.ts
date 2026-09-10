import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  fullName: string
  phone?: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: {
    id: string
    email: string
    fullName: string
    role: string
    tenantId: string | null
  }
}

// 對齊後端 UserDataExportResponse（Sprint 153，Sprint 149 §7 範圍外項目：補前端入口）
export interface UserDataExport {
  profile: {
    userId: string
    email: string
    fullName: string
    phone: string | null
    role: string
    createdAt: string
  }
  orders: unknown[]
  bookings: unknown[]
  productReviews: unknown[]
  bookingReviews: unknown[]
  addresses: unknown[]
  notificationPreferences: unknown[]
  notifications: unknown[]
  supportTickets: unknown[]
  oauthProviders: string[]
  tenantMemberships: unknown[]
  knownLimitations: string[]
  exportedAt: string
}

export interface ApiResponse<T> {
  success: boolean
  code?: string
  message?: string
  data: T
  errors?: Array<{
    field: string
    message: string
    rejectedValue?: unknown
  }>
}

class AuthService {
  async login(request: LoginRequest): Promise<AuthResponse> {
    const response = await apiClient.post<ApiResponse<AuthResponse>>(
      API_ENDPOINTS.auth.login,
      request
    )
    return response.data.data
  }

  async register(request: RegisterRequest): Promise<AuthResponse> {
    const response = await apiClient.post<ApiResponse<AuthResponse>>(
      API_ENDPOINTS.auth.register,
      request
    )
    return response.data.data
  }

  async refreshToken(refreshToken: string): Promise<AuthResponse> {
    const response = await apiClient.post<ApiResponse<AuthResponse>>(
      API_ENDPOINTS.auth.refresh,
      { refreshToken }
    )
    return response.data.data
  }

  // Logout: 通知後端失效 refresh token，再清除本機資料
  // 後端呼叫刻意 best-effort（catch 吞掉錯誤）——網路異常或 token 已過期
  // 都不應阻擋使用者登出，本機資料仍會被清除
  async logout(): Promise<void> {
    if (typeof window !== 'undefined') {
      const refreshToken = localStorage.getItem('refreshToken')
      try {
        await apiClient.post(API_ENDPOINTS.auth.logout, refreshToken ? { refreshToken } : {})
      } catch {
        // best-effort，忽略錯誤
      }
    }
    this.clearAuthData()
  }

  // Store auth data in localStorage
  storeAuthData(authResponse: AuthResponse): void {
    if (typeof window !== 'undefined') {
      localStorage.setItem('accessToken', authResponse.accessToken)
      localStorage.setItem('refreshToken', authResponse.refreshToken)
      localStorage.setItem('user', JSON.stringify(authResponse.user))
      if (authResponse.user.tenantId) {
        localStorage.setItem('tenantId', authResponse.user.tenantId)
      }
    }
  }

  // Clear auth data from localStorage
  clearAuthData(): void {
    if (typeof window !== 'undefined') {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('user')
      localStorage.removeItem('tenantId')
    }
  }

  // 會員資料匯出（PRD §1.5.1，Sprint 153：補前端入口，後端 Sprint 94 早已完成）
  async exportMyData(): Promise<UserDataExport> {
    const response = await apiClient.get<ApiResponse<UserDataExport>>(API_ENDPOINTS.auth.dataExport)
    return response.data.data
  }

  // 會員自助帳戶刪除／被遺忘權（PRD §1.5.1，Sprint 153：補前端入口）
  // 成功後後端已將此使用者全部 refresh token 加入黑名單，呼叫端須自行清除本機資料並導向登出後頁面
  async deleteMyAccount(): Promise<void> {
    await apiClient.delete(API_ENDPOINTS.auth.deleteMe)
  }

  // Get current user from localStorage
  getCurrentUser(): AuthResponse['user'] | null {
    if (typeof window === 'undefined') return null
    const userStr = localStorage.getItem('user')
    return userStr ? JSON.parse(userStr) : null
  }

  // Check if user is authenticated
  isAuthenticated(): boolean {
    if (typeof window === 'undefined') return false
    return !!localStorage.getItem('accessToken')
  }
}

export default new AuthService()
