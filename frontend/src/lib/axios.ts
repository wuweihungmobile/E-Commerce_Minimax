import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios'
import { API_CONFIG } from './api'

// Create axios instance
const apiClient: AxiosInstance = axios.create({
  baseURL: API_CONFIG.baseUrl,
  timeout: API_CONFIG.timeout,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Add auth token if available
    if (typeof window !== 'undefined') {
      const token = localStorage.getItem('accessToken')
      if (token && config.headers) {
        config.headers.Authorization = `Bearer ${token}`
      }

      // Add tenant header if available
      const tenantId = localStorage.getItem('tenantId')
      if (tenantId && config.headers) {
        config.headers['X-Tenant-ID'] = tenantId
      }
    }

    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// DEF-219：後端 refreshToken() 改為 rotation（換發後舊 token 立即失效並偵測重放）。
// 若多個並發請求同時收到 401，各自獨立呼叫 /v2/auth/refresh 會讓除了第一個以外的請求
// 都拿著「已被換發過」的舊 token 去打，被後端判定為重放攻擊、撤銷全部 session。
// 這裡以單一共用 Promise 讓同一批 401 只觸發一次真正的 refresh 呼叫，其餘請求等待並共用結果。
let refreshPromise: Promise<string> | null = null

// Response interceptor
apiClient.interceptors.response.use(
  (response) => {
    return response
  },
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    // Handle 401 Unauthorized
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      try {
        const refreshToken = localStorage.getItem('refreshToken')
        if (refreshToken) {
          if (!refreshPromise) {
            refreshPromise = axios
              .post(`${API_CONFIG.baseUrl}/v2/auth/refresh`, { refreshToken })
              .then((response) => {
                const { accessToken, refreshToken: newRefreshToken } = response.data.data
                localStorage.setItem('accessToken', accessToken)
                localStorage.setItem('refreshToken', newRefreshToken)
                return accessToken as string
              })
              .finally(() => {
                refreshPromise = null
              })
          }

          const accessToken = await refreshPromise

          if (originalRequest.headers) {
            originalRequest.headers.Authorization = `Bearer ${accessToken}`
          }

          return apiClient(originalRequest)
        }
      } catch (refreshError) {
        // Refresh failed, clear tokens and redirect to login
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
        localStorage.removeItem('user')

        if (typeof window !== 'undefined') {
          window.location.href = '/login'
        }

        return Promise.reject(refreshError)
      }
    }

    return Promise.reject(error)
  }
)

export default apiClient
