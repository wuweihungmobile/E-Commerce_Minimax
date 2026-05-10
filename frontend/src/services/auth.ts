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
