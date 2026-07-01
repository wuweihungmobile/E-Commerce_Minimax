import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

// M14 分析 / 儀表板統計（對齊後端 AnalyticsDto）
export interface DashboardStats {
  todayRevenue: number
  yesterdayRevenue: number
  monthRevenue: number
  yearRevenue: number
  revenueGrowthPercent: number
  todayOrders: number
  yesterdayOrders: number
  monthOrders: number
  pendingOrders: number
  activeListings: number
  totalRooms: number
  totalProducts: number
}

export interface OrderStats {
  totalOrders: number
  pendingPayment: number
  pendingShipment: number
  inTransit: number
  delivered: number
  completed: number
  cancelled: number
  refunded: number
  ordersByStatus: Record<string, number>
}

export interface DailyRevenue {
  date: string
  revenue: number
  orderCount: number
}

export interface RevenueStats {
  totalRevenue: number
  totalOrders: number
  averageOrderValue: number
  totalRefunds: number
  netRevenue: number
  currency: string
  dailyRevenue: {
    startDate: string
    endDate: string
    data: DailyRevenue[]
  }
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

class AnalyticsService {
  async getDashboardStats(): Promise<DashboardStats> {
    const response = await apiClient.get<ApiResponse<DashboardStats>>(
      API_ENDPOINTS.analytics.stats
    )
    return response.data.data
  }

  async getOrderStats(): Promise<OrderStats> {
    const response = await apiClient.get<ApiResponse<OrderStats>>(
      API_ENDPOINTS.analytics.orders
    )
    return response.data.data
  }

  async getRevenueStats(startDate?: string, endDate?: string): Promise<RevenueStats> {
    const params = new URLSearchParams()
    if (startDate) params.append('startDate', startDate)
    if (endDate) params.append('endDate', endDate)
    const query = params.toString()
    const response = await apiClient.get<ApiResponse<RevenueStats>>(
      API_ENDPOINTS.analytics.revenue + (query ? '?' + query : '')
    )
    return response.data.data
  }
}

export default new AnalyticsService()
