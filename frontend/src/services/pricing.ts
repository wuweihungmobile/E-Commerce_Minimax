import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'
import type { CalendarDay } from '@/services/booking'

export type PricingRuleType =
  | 'WEEKDAY_WEEKEND'
  | 'SEASONAL'
  | 'EARLY_BIRD'
  | 'LONG_STAY'
  | 'MANUAL_OVERRIDE'
  | 'LAST_MINUTE'

/**
 * 各規則型別的 config 參數（AI-2404）。與後端 jsonb config key 對齊：
 * 早鳥 minDaysAhead、長住 minNights、末班車 maxDaysAhead、折扣 discountPercent、
 * 平假日 weekendMultiplier、季節 multiplier、手動覆蓋 price。
 */
export interface EarlyBirdConfig { minDaysAhead: number; discountPercent: number }
export interface LongStayConfig { minNights: number; discountPercent: number }
export interface LastMinuteConfig { maxDaysAhead: number; discountPercent: number }
export interface WeekdayWeekendConfig { weekendMultiplier: number }
export interface SeasonalConfig { multiplier: number }
export interface ManualOverrideConfig { price: number }

export type PricingRuleConfig =
  | EarlyBirdConfig
  | LongStayConfig
  | LastMinuteConfig
  | WeekdayWeekendConfig
  | SeasonalConfig
  | ManualOverrideConfig

export interface PricingRule {
  ruleId: string
  tenantId: string
  roomListingId: string
  ruleType: PricingRuleType
  ruleName: string
  priority: number
  config: Record<string, unknown>
  validFrom: string
  validTo: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface CreatePricingRuleRequest {
  roomListingId: string
  ruleType: PricingRuleType
  ruleName: string
  priority?: number
  config: Record<string, unknown>
  validFrom: string
  validTo: string
  isActive?: boolean
}

export interface UpdatePricingRuleRequest {
  ruleName?: string
  priority?: number
  config?: Record<string, unknown>
  validFrom?: string
  validTo?: string
  isActive?: boolean
}

export interface CalculatePriceRequest {
  roomListingId: string
  checkInDate: string
  checkOutDate: string
  guestCount?: number
}

export interface PriceBreakdown {
  date: string
  basePrice: number
  adjustedPrice: number
  appliedRuleName: string
  adjustmentType: 'PERCENTAGE' | 'FIXED_AMOUNT'
  adjustmentValue: number
}

export interface CalculatePriceResponse {
  roomListingId: string
  checkInDate: string
  checkOutDate: string
  nights: number
  baseTotal: number
  adjustedTotal: number
  discount: number
  currency: string
  breakdown: PriceBreakdown[]
}

export interface SetCalendarPriceRequest {
  roomListingId: string
  date: string
  price: number
  reason?: string
}

export interface CalendarPriceResponse {
  roomListingId: string
  date: string
  price: number
  priceType: 'BASE' | 'SEASONAL' | 'MANUAL'
  appliedRuleName: string
  updatedAt: string
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
  errors?: Array<{
    field: string
    message: string
    rejectedValue?: unknown
  }>
}

class PricingService {
  async getRules(roomListingId?: string, activeOnly = false): Promise<PricingRule[]> {
    const params = new URLSearchParams()
    if (roomListingId) params.append('roomListingId', roomListingId)
    params.append('activeOnly', String(activeOnly))

    const response = await apiClient.get<ApiResponse<PricingRule[]>>(
      API_ENDPOINTS.pricing.rules + '?' + params.toString()
    )
    return response.data.data
  }

  async createRule(request: CreatePricingRuleRequest): Promise<PricingRule> {
    const response = await apiClient.post<ApiResponse<PricingRule>>(
      API_ENDPOINTS.pricing.createRule,
      request
    )
    return response.data.data
  }

  async updateRule(id: string, request: UpdatePricingRuleRequest): Promise<PricingRule> {
    const response = await apiClient.put<ApiResponse<PricingRule>>(
      API_ENDPOINTS.pricing.updateRule(id),
      request
    )
    return response.data.data
  }

  async deleteRule(id: string): Promise<void> {
    await apiClient.delete(API_ENDPOINTS.pricing.deleteRule(id))
  }

  async calculatePrice(request: CalculatePriceRequest): Promise<CalculatePriceResponse> {
    const response = await apiClient.post<ApiResponse<CalculatePriceResponse>>(
      API_ENDPOINTS.pricing.calculate,
      request
    )
    return response.data.data
  }

  async setCalendarPrice(request: SetCalendarPriceRequest): Promise<CalendarPriceResponse> {
    const response = await apiClient.post<ApiResponse<CalendarPriceResponse>>(
      API_ENDPOINTS.pricing.calendarPrice,
      request
    )
    return response.data.data
  }

  // 房東後台預覽定價日曆（Sprint 83，PRD P0：未來 90 天定價日曆預覽）。
  // 回傳結構與買家整月日曆（bookingService.getCalendar）完全相同，僅多了租戶擁有權檢查。
  async getCalendarPreview(
    roomListingId: string,
    startDate: string,
    endDate: string
  ): Promise<CalendarDay[]> {
    const params = new URLSearchParams({ roomListingId, startDate, endDate })
    const response = await apiClient.get<ApiResponse<CalendarDay[]>>(
      API_ENDPOINTS.pricing.calendar + '?' + params.toString()
    )
    return response.data.data
  }
}

export default new PricingService()