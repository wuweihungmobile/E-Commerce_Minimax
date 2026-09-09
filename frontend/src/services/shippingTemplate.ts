import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

// 對齊後端 ShippingTemplateDto（M11 US-006，商家運費模板，Sprint 150 補前端入口）

export type ShippingFeeType = 'FIXED' | 'FREE_THRESHOLD'

export interface ShippingTemplate {
  id: string
  tenantId: string
  name: string
  feeType: ShippingFeeType
  fixedAmount: number | null
  freeThreshold: number | null
  createdAt: string
  updatedAt: string
}

export interface ShippingTemplateCreateInput {
  name: string
  feeType: ShippingFeeType
  fixedAmount?: number
  freeThreshold?: number
}

// 後端 UpdateRequest 不含 feeType——建立後類型不可變更（見 ShippingTemplateDto.UpdateRequest）
export interface ShippingTemplateUpdateInput {
  name?: string
  fixedAmount?: number
  freeThreshold?: number
}

interface ApiResponse<T> {
  success: boolean
  code?: string
  message?: string
  data: T
}

export const SHIPPING_FEE_TYPE_LABELS: Record<ShippingFeeType, string> = {
  FIXED: '固定運費',
  FREE_THRESHOLD: '滿額免運',
}

class ShippingTemplateService {
  async list(): Promise<ShippingTemplate[]> {
    const response = await apiClient.get<ApiResponse<ShippingTemplate[]>>(
      API_ENDPOINTS.shippingTemplates.list
    )
    return response.data.data ?? []
  }

  async create(input: ShippingTemplateCreateInput): Promise<ShippingTemplate> {
    const response = await apiClient.post<ApiResponse<ShippingTemplate>>(
      API_ENDPOINTS.shippingTemplates.create,
      input
    )
    return response.data.data
  }

  async update(id: string, input: ShippingTemplateUpdateInput): Promise<ShippingTemplate> {
    const response = await apiClient.put<ApiResponse<ShippingTemplate>>(
      API_ENDPOINTS.shippingTemplates.update(id),
      input
    )
    return response.data.data
  }

  async remove(id: string): Promise<void> {
    await apiClient.delete(API_ENDPOINTS.shippingTemplates.delete(id))
  }
}

export default new ShippingTemplateService()
